package app.quieta.service

import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import app.quieta.core.auto.AutoMuteCoordinator
import app.quieta.core.engine.NotificationTimelineStore
import app.quieta.core.repo.RuleRepository
import app.quieta.core.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Lightweight listener: timeline identity fields + auto-mute hook.
 * Never persists notification content.
 */
class QuietaNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var settings: AppSettings
    private lateinit var rules: RuleRepository
    private lateinit var timeline: NotificationTimelineStore
    private val timelineEnabled by lazy {
        settings.notificationTimelineEnabled.stateIn(scope, SharingStarted.Eagerly, true)
    }
    private val autoMuteEnabled by lazy {
        settings.autoMuteNewChannels.stateIn(scope, SharingStarted.Eagerly, false)
    }

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        rules = RuleRepository.getInstance(this)
        timeline = NotificationTimelineStore.getInstance(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "listener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val pkg = sbn.packageName ?: return
        val channelId = notification.channelId ?: return
        val appLabel = resolveAppLabel(pkg)
        val (channelName, channelImportance) = resolveChannel(pkg, channelId)
        // Notification.priority is not the same as channel importance; prefer channel.
        val importance = if (channelImportance >= 0) channelImportance else -1
        scope.launch {
            if (timelineEnabled.value) {
                timeline.append(
                    packageName = pkg,
                    appLabel = appLabel,
                    channelId = channelId,
                    channelName = channelName,
                    importance = importance,
                )
            }
            AutoMuteCoordinator.onChannelSeen(
                context = applicationContext,
                packageName = pkg,
                channelId = channelId,
                channelName = channelName.ifBlank { channelId },
                importance = if (importance >= 0) importance else notification.priority,
                repository = rules,
                autoMuteEnabled = autoMuteEnabled.value,
            )
        }
    }

    private fun resolveAppLabel(packageName: String): String {
        return runCatching {
            val ai = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(ai).toString()
        }.getOrDefault(packageName)
    }

    /** Best-effort channel name/importance; may be unavailable without extra privilege. */
    private fun resolveChannel(packageName: String, channelId: String): Pair<String, Int> {
        return runCatching {
            val nm = getSystemService(NotificationManager::class.java) ?: return@runCatching channelId to -1
            val channel = nm.getNotificationChannel(channelId) ?: return@runCatching channelId to -1
            val name = channel.name?.toString().orEmpty().ifBlank { channelId }
            name to channel.importance
        }.getOrDefault(channelId to -1)
    }

    companion object {
        private const val TAG = "QuietaNLS"
    }
}
