// SPDX-License-Identifier: GPL-3.0-only
package app.quieta.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.quieta.R
import app.quieta.core.settings.ListenerFlagStore
import app.quieta.util.log.QLog
import java.text.DateFormat
import java.util.Date

/**
 * Opt-in continuous-collection FGS. Default off.
 * Lives in `:listener` so the UI task can be swiped without this service
 * being the recents target. Cost: a low-priority notification + resident process.
 * No polling — only rebinds NLS and keeps the process eligible to receive events.
 */
class TimelineKeepAliveService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        if (!promoteToForeground()) {
            stopSelf()
            return
        }
        NotificationListenerAccess.ensureBound(this, "keep_alive_create")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val stillOn = ListenerFlagStore.readFlags(this).keepAliveEnabled
        if (!stillOn) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!promoteToForeground()) {
            // HyperOS may reject specialUse FGS when notifications/battery block us.
            stopSelf()
            return START_NOT_STICKY
        }
        NotificationListenerAccess.ensureBound(this, "keep_alive_start")
        refreshStatusNotification(this)
        // Opt-in mode intentionally sticky so ROM restarts after low-priority kills.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ListenerFlagStore.markKeepAlive(this, false)
        handler.removeCallbacksAndMessages(null)
        QLog.i(QLog.TAG_TIMELINE, "keep-alive service destroyed")
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        QLog.w(QLog.TAG_TIMELINE, "keep-alive onTaskRemoved")
        NotificationListenerAccess.ensureBound(this, "keep_alive_task_removed")
        val stillOn = ListenerFlagStore.readFlags(this).keepAliveEnabled
        if (stillOn) {
            // Some ROMs drop FGS when the recents card is swiped; re-promote shortly.
            handler.postDelayed({
                if (ListenerFlagStore.readFlags(this).keepAliveEnabled) {
                    if (!promoteToForeground()) {
                        runCatching {
                            val intent = Intent(applicationContext, TimelineKeepAliveService::class.java)
                            androidx.core.content.ContextCompat.startForegroundService(applicationContext, intent)
                        }
                    }
                    NotificationListenerAccess.ensureBound(this, "keep_alive_task_removed_retry")
                }
            }, 800L)
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun promoteToForeground(): Boolean {
        val notification = buildNotification(this)
        val ok = runCatching {
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
        }.isSuccess
        if (ok) {
            ListenerFlagStore.markKeepAlive(this, true)
        } else {
            QLog.w(QLog.TAG_TIMELINE, "keep-alive startForeground failed")
            ListenerFlagStore.markKeepAlive(this, false)
        }
        return ok
    }

    companion object {
        private const val NOTIFICATION_ID = 7102
        private const val CHANNEL_ID = "quieta_timeline_keepalive"

        /** Update keep-alive notification text after NLS connect/disconnect. */
        fun refreshStatusNotification(context: Context) {
            val app = context.applicationContext
            if (!ListenerFlagStore.readFlags(app).keepAliveEnabled) return
            if (ListenerFlagStore.readState(app).keepAliveStartedAt <= 0L) return
            val manager = app.getSystemService(NotificationManager::class.java) ?: return
            ensureChannel(app, manager)
            runCatching { manager.notify(NOTIFICATION_ID, buildNotification(app)) }
        }

        private fun ensureChannel(context: Context, manager: NotificationManager) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.timeline_keepalive_channel),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }

        private fun buildNotification(context: Context): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager != null) ensureChannel(context, manager)
            val state = ListenerFlagStore.readState(context)
            val connected = NotificationListenerAccess.isConnected(context)
            val statusText = when {
                connected -> context.getString(R.string.timeline_keepalive_text_connected)
                state.lastEventAt > 0L -> {
                    val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(state.lastEventAt))
                    context.getString(R.string.timeline_keepalive_text_waiting, time)
                }
                else -> context.getString(R.string.timeline_keepalive_text)
            }
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.timeline_keepalive_title))
                .setContentText(statusText)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        }
    }
}
