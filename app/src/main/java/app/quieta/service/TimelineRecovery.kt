// SPDX-License-Identifier: GPL-3.0-only
package app.quieta.service

import android.app.ActivityManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import app.quieta.core.settings.AppSettings
import app.quieta.core.settings.ListenerFlagStore
import app.quieta.util.log.QLog
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Timeline recovery policy after recents-kill / HyperOS unbind.
 *
 * Default remains non-resident: open-app rebind + one-shot boot rebind.
 * Opt-in layers: rare JobScheduler health check, then FGS keep-alive.
 * NLS / Job / FGS share the `:listener` process so swipe-away of the UI
 * task is less likely to stop collection when the system still binds NLS.
 */
object TimelineRecovery {

    const val LISTENER_PROCESS_SUFFIX = ":listener"
    private const val HEALTH_JOB_ID = 7101
    private const val HEALTH_PERIOD_MS = 20L * 60L * 1000L
    private const val CYCLE_MIN_INTERVAL_MS = 30L * 60L * 1000L
    private const val TAG = "TimelineRecovery"

    fun isListenerProcess(context: Context): Boolean =
        currentProcessName(context).endsWith(LISTENER_PROCESS_SUFFIX)

    /** Own package `:listener` process must be running for a stale NLS flag to count. */
    fun isListenerProcessAlive(context: Context): Boolean {
        val target = context.applicationContext.packageName + LISTENER_PROCESS_SUFFIX
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return am.runningAppProcesses?.any { it.processName == target } == true
    }

    fun currentProcessName(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ApplicationProcessName.get()
        }
        val pid = Process.myPid()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return ""
        return am.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName.orEmpty()
    }

    private object ApplicationProcessName {
        fun get(): String = android.app.Application.getProcessName()
    }

    fun canCycleComponent(context: Context): Boolean {
        // Force-stop/swipe-kill leaves no listener process; HyperOS often needs a
        // component cycle immediately — do not rate-limit that case.
        if (!isListenerProcessAlive(context)) return true
        val last = ListenerFlagStore.readState(context).lastCycleAt
        return last <= 0L || System.currentTimeMillis() - last >= CYCLE_MIN_INTERVAL_MS
    }

    /** Seed listener flags from DataStore and apply opt-in recovery layers. */
    suspend fun syncFromSettings(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val settings = AppSettings(app)
        val flags = ListenerFlagStore.Flags(
            timelineEnabled = runCatching { settings.notificationTimelineEnabled.first() }.getOrDefault(true),
            autoMuteEnabled = runCatching { settings.autoMuteNewChannels.first() }.getOrDefault(false),
            healthCheckEnabled = runCatching { settings.timelineHealthCheckEnabled.first() }.getOrDefault(false),
            keepAliveEnabled = runCatching { settings.timelineKeepAliveEnabled.first() }.getOrDefault(false),
        )
        ListenerFlagStore.writeFlags(app, flags)
        if (flags.healthCheckEnabled) scheduleHealthJob(app) else cancelHealthJob(app)
        if (flags.keepAliveEnabled) startKeepAlive(app) else stopKeepAlive(app)
        QLog.d(TAG, "sync flags=$flags")
    }

    fun applyFlags(context: Context, flags: ListenerFlagStore.Flags) {
        ListenerFlagStore.writeFlags(context.applicationContext, flags)
        if (flags.healthCheckEnabled) scheduleHealthJob(context) else cancelHealthJob(context)
        if (flags.keepAliveEnabled) startKeepAlive(context) else stopKeepAlive(context)
    }

    fun scheduleHealthJob(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        val component = ComponentName(context.applicationContext, TimelineKeepAliveJobService::class.java)
        val info = JobInfo.Builder(HEALTH_JOB_ID, component)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
            .setPersisted(true)
            .setPeriodic(HEALTH_PERIOD_MS)
            .build()
        val result = runCatching { scheduler.schedule(info) }.getOrElse { JobScheduler.RESULT_FAILURE }
        QLog.i(TAG, "scheduleHealthJob result=$result")
    }

    fun cancelHealthJob(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        runCatching { scheduler.cancel(HEALTH_JOB_ID) }
        QLog.i(TAG, "cancelHealthJob")
    }

    fun isHealthJobScheduled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return false
        return scheduler.allPendingJobs.any { it.id == HEALTH_JOB_ID }
    }

    fun startKeepAlive(context: Context) {
        val intent = Intent(context.applicationContext, TimelineKeepAliveService::class.java)
        runCatching {
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }.onFailure {
            QLog.w(TAG, "startKeepAlive failed", it)
        }
    }

    fun stopKeepAlive(context: Context) {
        runCatching {
            context.applicationContext.stopService(
                Intent(context.applicationContext, TimelineKeepAliveService::class.java),
            )
        }
    }

    fun jobId(): Int = abs(HEALTH_JOB_ID)
}
