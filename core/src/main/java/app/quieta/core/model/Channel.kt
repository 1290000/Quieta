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
)

data class AppChannels(
    val packageName: String,
    val appLabel: String,
    val channels: List<Channel>,
    /** System / updated-system package. Used for list filter + marketing heuristics. */
    val isSystem: Boolean = false,
)
