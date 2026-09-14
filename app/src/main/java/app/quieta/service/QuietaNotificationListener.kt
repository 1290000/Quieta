package app.quieta.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import app.quieta.core.auto.AutoMuteCoordinator
import app.quieta.core.repo.RuleRepository
import app.quieta.core.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Lightweight listener: only feeds AutoMuteCoordinator when the user enabled auto-mute.
 * Does not persist notification content.
 */
class QuietaNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var settings: AppSettings
    private lateinit var rules: RuleRepository

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        rules = RuleRepository(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "listener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val pkg = sbn.packageName ?: return
        val channelId = notification.channelId ?: return
        // Channel display name is not on StatusBarNotification; use id.
        scope.launch {
            val enabled = settings.autoMuteNewChannels.first()
            AutoMuteCoordinator.onChannelSeen(
                packageName = pkg,
                channelId = channelId,
                channelName = channelId,
                importance = notification.priority,
                repository = rules,
                autoMuteEnabled = enabled,
            )
        }
    }

    companion object {
        private const val TAG = "QuietaNLS"
    }
}
