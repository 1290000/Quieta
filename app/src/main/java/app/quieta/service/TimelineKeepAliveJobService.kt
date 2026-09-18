// SPDX-License-Identifier: GPL-3.0-only
package app.quieta.service

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Handler
import android.os.Looper
import app.quieta.core.settings.ListenerFlagStore
import app.quieta.util.log.QLog

/**
 * T3: rare, opt-in health check. No wakelock, no network.
 * Only asks the system to rebind NLS when enabled but not live.
 */
class TimelineKeepAliveJobService : JobService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onStartJob(params: JobParameters?): Boolean {
        val context = applicationContext
        val flags = ListenerFlagStore.readFlags(context)
        if (!flags.timelineEnabled && !flags.autoMuteEnabled) {
            QLog.d(QLog.TAG_TIMELINE, "health job skip: collection off")
            jobFinished(params, false)
            return false
        }
        QLog.i(QLog.TAG_TIMELINE, "health job ensureBound")
        NotificationListenerAccess.ensureBound(context, "health_job")
        handler.postDelayed({
            jobFinished(params, false)
        }, 1_500L)
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        handler.removeCallbacksAndMessages(null)
        return false
    }
}
