// SPDX-License-Identifier: GPL-3.0-only
package app.quieta.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.quieta.util.log.QLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * T1: one-shot rebind after boot / package replace.
 * Near-zero steady-state cost — no service is started here.
 */
class TimelineBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        val app = context.applicationContext
        QLog.i(QLog.TAG_TIMELINE, "boot/package event=$action")
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                TimelineRecovery.syncFromSettings(app)
                NotificationListenerAccess.ensureBound(app, "boot_$action")
            } finally {
                pending.finish()
            }
        }
    }
}
