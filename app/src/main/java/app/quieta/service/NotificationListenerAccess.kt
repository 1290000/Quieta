// SPDX-License-Identifier: GPL-3.0-only
package app.quieta.service

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import app.quieta.core.settings.ListenerFlagStore
import app.quieta.util.log.QLog
import java.util.concurrent.atomic.AtomicBoolean

/**
 * HyperOS / MIUI often drop the NLS binder after process death or battery
 * restrictions even when the user still has 通知使用权 enabled. The app must
 * re-request binding or the timeline silently freezes on stale disk data.
 *
 * Plain requestRebind() is not enough on HyperOS after reinstall/force-stop;
 * we also cycle the component so NotificationManagerService binds again.
 * Component cycle is rate-limited — it is a PackageManager write, not free.
 */
object NotificationListenerAccess {

    private val rebinding = AtomicBoolean(false)

    fun component(context: Context): ComponentName =
        ComponentName(context.applicationContext, QuietaNotificationListener::class.java)

    fun isEnabled(context: Context): Boolean {
        val pkg = context.applicationContext.packageName
        return runCatching {
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(pkg)
        }.getOrDefault(false)
    }

    /**
     * True when a live NLS binder callback exists.
     * Listener process writes [ListenerFlagStore.NlsState]; UI may be another process.
     * Force-stop/swipe-kill never runs onListenerDisconnected — a stale `connected=true`
     * file must not skip rebind, so also require the `:listener` process to be alive.
     *
     * Display-only: [ensureBound] treats the in-process static as authoritative for
     * skip decisions because a newly spawned `:listener` can outlive a stale file
     * without yet holding the system NLS binder.
     */
    fun isConnected(context: Context? = null): Boolean {
        if (QuietaNotificationListener.isConnected) return true
        val app = context?.applicationContext ?: return false
        if (!ListenerFlagStore.readState(app).connected) return false
        return TimelineRecovery.isListenerProcessAlive(app)
    }

    fun lastEventAt(context: Context): Long = ListenerFlagStore.readState(context).lastEventAt

    /** Ask the system to rebind if the listener is enabled but not live. */
    fun ensureBound(context: Context, reason: String = "manual") {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val app = context.applicationContext
        if (!isEnabled(app)) {
            QLog.d(QLog.TAG_TIMELINE, "rebind skipped ($reason): listener not enabled")
            return
        }
        // Skip only on a live binder callback in this process. After force-stop the
        // nls_state.json file stays `connected=true` while the new :listener process
        // has not been bound yet — trusting that file freezes the timeline on HyperOS.
        if (QuietaNotificationListener.isConnected) {
            QLog.d(QLog.TAG_TIMELINE, "rebind skipped ($reason): already connected")
            return
        }
        if (!rebinding.compareAndSet(false, true)) return
        val cn = component(app)
        runCatching {
            NotificationListenerService.requestRebind(cn)
            QLog.i(QLog.TAG_TIMELINE, "requestRebind reason=$reason pkg=${app.packageName}")
        }.onFailure {
            Log.w(TAG, "requestRebind failed reason=$reason", it)
        }
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (!QuietaNotificationListener.isConnected) {
                    if (TimelineRecovery.canCycleComponent(app)) {
                        cycleComponent(app, cn, reason)
                    } else {
                        runCatching { NotificationListenerService.requestRebind(cn) }
                        QLog.i(QLog.TAG_TIMELINE, "rebind retry only (cycle rate-limited) reason=$reason")
                    }
                }
            } finally {
                rebinding.set(false)
            }
        }, 500L)
    }

    /**
     * Disable/enable the listener component then requestRebind again.
     * HyperOS ignores requestRebind when it has no bind record; a component
     * state change forces NotificationManagerService to reconcile bindings.
     */
    private fun cycleComponent(context: Context, cn: ComponentName, reason: String) {
        runCatching {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                cn,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            pm.setComponentEnabledSetting(
                cn,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            NotificationListenerService.requestRebind(cn)
            ListenerFlagStore.markCycle(context)
            QLog.i(QLog.TAG_TIMELINE, "component cycle + rebind reason=$reason")
        }.onFailure {
            Log.w(TAG, "component cycle failed reason=$reason", it)
            QLog.w(QLog.TAG_TIMELINE, "component cycle failed reason=$reason", it)
        }
    }

    private const val TAG = "QuietaNLSAccess"
}
