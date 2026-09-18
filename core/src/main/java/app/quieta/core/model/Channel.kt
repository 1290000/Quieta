package app.quieta.core.model

/** Importance mirror of NotificationManager for domain layer. */
enum class ChannelImportance {
    NONE,
    MIN,
    LOW,
    DEFAULT,
    HIGH,
}

data class Channel(
    val packageName: String,
    val id: String,
    val name: String,
    val importance: ChannelImportance,
    /** True when the system channel still has a non-empty sound URI. */
    val soundEnabled: Boolean = true,
    /** Channel-level vibrate flag. */
    val vibrationEnabled: Boolean = false,
    /** True when lockscreen visibility hides the notification (VISIBILITY_SECRET / NO). */
    val lockscreenHidden: Boolean = false,
) {
    /**
     * Whether this channel will audibly alert: needs a sound URI *and*
     * DEFAULT/HIGH importance. HyperOS/Android keep the default URI on MIN/LOW
     * channels that do not actually play sound.
     */
    val effectiveSoundEnabled: Boolean
        get() = soundEnabled && when (importance) {
            ChannelImportance.DEFAULT, ChannelImportance.HIGH -> true
            ChannelImportance.NONE, ChannelImportance.MIN, ChannelImportance.LOW -> false
        }
}

data class AppChannels(
    val packageName: String,
    val appLabel: String,
    val channels: List<Channel>,
    /** System / updated-system package. Used for list filter + marketing heuristics. */
    val isSystem: Boolean = false,
)
