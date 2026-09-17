package app.quieta.service

import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import app.quieta.core.auto.AutoMuteCoordinator
import app.quieta.core.engine.NotificationTimelineStore
import app.quieta.core.model.ChannelImportance
import app.quieta.core.repo.ChannelInventoryStore
import app.quieta.core.repo.RuleRepository
import app.quieta.core.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lightweight listener: timeline identity fields + auto-mute hook.
 * Never persists notification content.
 */
class QuietaNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var settings: AppSettings
    private lateinit var rules: RuleRepository
    private lateinit var timeline: NotificationTimelineStore
    private lateinit var inventory: ChannelInventoryStore
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
        inventory = ChannelInventoryStore.getInstance(this)
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
        scope.launch {
            val (channelName, channelImportance) = resolveChannel(pkg, channelId)
            if (timelineEnabled.value) {
                timeline.append(
                    packageName = pkg,
                    appLabel = appLabel,
                    channelId = channelId,
                    channelName = channelName,
                    importance = channelImportance,
                )
            }
            AutoMuteCoordinator.onChannelSeen(
                context = applicationContext,
                packageName = pkg,
                channelId = channelId,
                channelName = channelName,
                importance = if (channelImportance >= 0) channelImportance else notification.priority,
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

    /**
     * getNotificationChannel() only works for the listener's own package.
     * Prefer the inventory cache written by Root/Shizuku scans, then fall back to the id.
     */
    private suspend fun resolveChannel(packageName: String, channelId: String): Pair<String, Int> {
        val fromCache = withContext(Dispatchers.IO) {
            runCatching {
                inventory.current()
                    .firstOrNull { it.packageName == packageName }
                    ?.channels
                    ?.firstOrNull { it.id == channelId }
            }.getOrNull()
        }
        if (fromCache != null) {
            return (fromCache.name.ifBlank { channelId }) to fromCache.importance.toInt()
        }
        // Last resort: self package (listener is app.quieta*); rarely useful for third-party.
        val self = runCatching {
            val nm = getSystemService(NotificationManager::class.java) ?: return@runCatching null
            nm.getNotificationChannel(channelId)?.let { ch ->
                (ch.name?.toString().orEmpty().ifBlank { channelId }) to ch.importance
            }
        }.getOrNull()
        return self ?: (channelId to -1)
    }

    private fun ChannelImportance.toInt(): Int = when (this) {
        ChannelImportance.NONE -> 0
        ChannelImportance.MIN -> 1
        ChannelImportance.LOW -> 2
        ChannelImportance.DEFAULT -> 3
        ChannelImportance.HIGH -> 4
    }

    companion object {
        private const val TAG = "QuietaNLS"
    }
}
