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
import app.quieta.core.settings.ListenerFlagStore
import app.quieta.util.log.QLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lightweight listener: timeline identity fields + auto-mute hook.
 * Never persists notification content.
 *
 * Runs in `:listener` so recents-swipe of the UI task is less likely to stop
 * collection while the system still holds the NLS binder.
 */
class QuietaNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var rules: RuleRepository
    private lateinit var timeline: NotificationTimelineStore
    private lateinit var inventory: ChannelInventoryStore

    override fun onCreate() {
        super.onCreate()
        // Process cold start / re-bind: static is false until onListenerConnected.
        // Clear the cross-process flag so UI does not trust a pre-force-stop leftover.
        isConnected = false
        ListenerFlagStore.markConnected(applicationContext, false)
        rules = RuleRepository.getInstance(this)
        timeline = NotificationTimelineStore.getInstance(this)
        inventory = ChannelInventoryStore.getInstance(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        ListenerFlagStore.markConnected(applicationContext, true)
        Log.i(TAG, "listener connected process=${TimelineRecovery.currentProcessName(this)}")
        QLog.i(QLog.TAG_TIMELINE, "listener connected process=${TimelineRecovery.currentProcessName(this)}")
        TimelineKeepAliveService.refreshStatusNotification(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        ListenerFlagStore.markConnected(applicationContext, false)
        Log.w(TAG, "listener disconnected")
        QLog.w(QLog.TAG_TIMELINE, "listener disconnected")
        TimelineKeepAliveService.refreshStatusNotification(applicationContext)
        // HyperOS may unbind after process death / battery restrictions; ask to rebind.
        NotificationListenerAccess.ensureBound(applicationContext, "onListenerDisconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val pkg = sbn.packageName ?: return
        val channelId = notification.channelId ?: return
        QLog.d(QLog.TAG_TIMELINE, "posted pkg=$pkg channel=$channelId")
        val flags = ListenerFlagStore.readFlags(this)
        if (!flags.timelineEnabled && !flags.autoMuteEnabled) return
        ListenerFlagStore.markEvent(applicationContext)
        if (flags.keepAliveEnabled) {
            TimelineKeepAliveService.refreshStatusNotification(applicationContext)
        }
        val appLabel = resolveAppLabel(pkg)
        scope.launch {
            val (channelName, channelImportance) = resolveChannel(pkg, channelId)
            val (soundOn, vibeOn) = resolveSoundVibration(pkg, channelId, channelImportance)
            if (flags.timelineEnabled) {
                timeline.append(
                    packageName = pkg,
                    appLabel = appLabel,
                    channelId = channelId,
                    channelName = channelName,
                    importance = channelImportance,
                    soundEnabled = soundOn,
                    vibrationEnabled = vibeOn,
                )
            }
            AutoMuteCoordinator.onChannelSeen(
                context = applicationContext,
                packageName = pkg,
                channelId = channelId,
                channelName = channelName,
                importance = if (channelImportance >= 0) channelImportance else notification.priority,
                repository = rules,
                autoMuteEnabled = flags.autoMuteEnabled,
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

    /**
     * Timeline sound flag is the *effective* sound: a channel only audibly alerts
     * when it still has a sound URI and importance is DEFAULT/HIGH. MIN/LOW/NONE
     * keep the default URI on HyperOS but do not make sound — report those as off.
     */
    private suspend fun resolveSoundVibration(
        packageName: String,
        channelId: String,
        channelImportance: Int,
    ): Pair<Boolean?, Boolean?> {
        val ch = withContext(Dispatchers.IO) {
            runCatching {
                inventory.current()
                    .firstOrNull { it.packageName == packageName }
                    ?.channels
                    ?.firstOrNull { it.id == channelId }
            }.getOrNull()
        } ?: return null to null
        val effectiveSound = when {
            !ch.soundEnabled -> false
            channelImportance >= 3 -> true
            channelImportance >= 0 -> false
            else -> ch.soundEnabled
        }
        return effectiveSound to ch.vibrationEnabled
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
        @Volatile
        var isConnected: Boolean = false
    }
}
