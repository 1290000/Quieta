package app.quieta.core.privilege

import android.app.Notification
import android.app.NotificationChannel
import android.media.AudioAttributes
import android.net.Uri
import android.provider.Settings
import app.quieta.core.model.Channel
import app.quieta.core.model.ChannelImportance

/**
 * Shared NotificationChannel mutation helpers for Shizuku / Dhizuku / Root backends.
 * Sound/vibration/lockscreen are applied on the same channel object as importance
 * so one Binder update can carry a full snapshot write.
 */
object NotificationChannelPatch {

    /** Default URI used when snapshot says sound is on but path is not stored. */
    fun defaultNotificationUri(): Uri? = runCatching {
        Settings.System.DEFAULT_NOTIFICATION_URI
    }.getOrNull()

    fun applyToChannel(channel: NotificationChannel, patch: ChannelSettingsPatch) {
        patch.importance?.let { importance ->
            val setter = channel.javaClass.methods.firstOrNull {
                it.name == "setImportance" && it.parameterCount == 1
            } ?: error("setImportance not found")
            setter.invoke(channel, importance)
        }
        patch.soundEnabled?.let { enabled ->
            val setSound = channel.javaClass.methods.firstOrNull {
                it.name == "setSound" && it.parameterCount == 2
            } ?: error("setSound not found")
            if (enabled) {
                val uri = defaultNotificationUri()
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound.invoke(channel, uri, attrs)
            } else {
                setSound.invoke(channel, null, null)
            }
        }
        patch.vibrationEnabled?.let { enabled ->
            val enable = channel.javaClass.methods.firstOrNull {
                it.name == "enableVibration" && it.parameterCount == 1
            } ?: error("enableVibration not found")
            enable.invoke(channel, enabled)
            // shouldVibrate() often requires a non-null pattern, not only the boolean flag.
            val setPattern = channel.javaClass.methods.firstOrNull {
                it.name == "setVibration" && it.parameterCount == 1
            }
            val currently = runCatching { channel.shouldVibrate() }.getOrDefault(false)
            if (enabled && !currently) {
                setPattern?.invoke(channel, longArrayOf(0L, 250L, 250L, 250L))
            } else if (!enabled && currently) {
                runCatching { setPattern?.invoke(channel, null) }
            }
        }
        patch.lockscreenHidden?.let { hidden ->
            val setter = channel.javaClass.methods.firstOrNull {
                it.name == "setLockscreenVisibility" && it.parameterCount == 1
            } ?: error("setLockscreenVisibility not found")
            // NotificationManager: PUBLIC=1, PRIVATE=0, SECRET=-1 (same mapping as list-side).
            val value = if (hidden) -1 else 1
            setter.invoke(channel, value)
        }
    }

    /**
     * True when the channel still carries a usable sound URI.
     * HyperOS keeps the default notification URI even when importance is too low
     * to audibly alert — callers that mean "will make sound" must also check importance
     * (see [app.quieta.core.model.Channel.effectiveSoundEnabled]).
     */
    fun soundEnabledOf(channel: NotificationChannel): Boolean {
        val uri = channel.sound ?: return false
        val value = uri.toString().trim()
        if (value.isEmpty()) return false
        if (value.equals("null", ignoreCase = true)) return false
        return true
    }

    fun vibrationEnabledOf(channel: NotificationChannel): Boolean =
        runCatching { channel.shouldVibrate() }.getOrDefault(false)

    fun lockscreenHiddenOf(channel: NotificationChannel): Boolean =
        runCatching {
            // -1 SECRET / 0 PRIVATE → hidden on lockscreen.
            channel.lockscreenVisibility == -1 || channel.lockscreenVisibility == 0
        }.getOrDefault(false)

    fun matches(channel: NotificationChannel, patch: ChannelSettingsPatch): List<String> {
        val issues = mutableListOf<String>()
        patch.importance?.let { expected ->
            val actual = channel.importance
            if (actual != expected) issues += "importance expected=$expected actual=$actual"
        }
        patch.soundEnabled?.let { expected ->
            val actual = soundEnabledOf(channel)
            if (actual != expected) issues += "sound expected=$expected actual=$actual"
        }
        patch.vibrationEnabled?.let { expected ->
            val actual = vibrationEnabledOf(channel)
            if (actual != expected) issues += "vibration expected=$expected actual=$actual"
        }
        patch.lockscreenHidden?.let { expected ->
            val actual = lockscreenHiddenOf(channel)
            if (actual != expected) issues += "lockscreenHidden expected=$expected actual=$actual"
        }
        return issues
    }

    fun toDomain(
        packageName: String,
        raw: NotificationChannel,
    ): Channel = Channel(
        packageName = packageName,
        id = raw.id,
        name = raw.name?.toString().orEmpty().ifEmpty { raw.id },
        importance = when (raw.importance) {
            0 -> ChannelImportance.NONE
            1 -> ChannelImportance.MIN
            2 -> ChannelImportance.LOW
            4, 5 -> ChannelImportance.HIGH
            else -> ChannelImportance.DEFAULT
        },
        soundEnabled = soundEnabledOf(raw),
        vibrationEnabled = vibrationEnabledOf(raw),
        lockscreenHidden = lockscreenHiddenOf(raw),
    )

    /** Silence default: keep AUDIO_ATTRIBUTES_DEFAULT equivalent for re-enabled sound. */
    @Suppress("unused")
    private val defaultAudioAttributes: AudioAttributes = Notification.AUDIO_ATTRIBUTES_DEFAULT
}
