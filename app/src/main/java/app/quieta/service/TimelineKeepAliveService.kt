// SPDX-License-Identifier: GPL-3.0-only
package app.quieta.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.quieta.R
import app.quieta.core.settings.ListenerFlagStore
import app.quieta.util.log.QLog

/**
 * T5: optional continuous-collection FGS. Default off.
 * Lives in `:listener` so the UI task can be swiped without this service
 * being the recents target. Cost: a low-priority notification + resident process.
 */
class TimelineKeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        promoteToForeground()
        NotificationListenerAccess.ensureBound(this, "keep_alive_create")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        NotificationListenerAccess.ensureBound(this, "keep_alive_start")
        val stillOn = ListenerFlagStore.readFlags(this).keepAliveEnabled
        if (!stillOn) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Opt-in mode intentionally sticky so ROM restarts after low-priority kills.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        QLog.i(QLog.TAG_TIMELINE, "keep-alive service destroyed")
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        QLog.w(QLog.TAG_TIMELINE, "keep-alive onTaskRemoved")
        super.onTaskRemoved(rootIntent)
    }

    private fun promoteToForeground() {
        val notification = buildNotification()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure {
            QLog.w(QLog.TAG_TIMELINE, "keep-alive startForeground failed", it)
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.timeline_keepalive_channel),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
            }
            manager?.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.timeline_keepalive_title))
            .setContentText(getString(R.string.timeline_keepalive_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 7102
        private const val CHANNEL_ID = "quieta_timeline_keepalive"
    }
}
