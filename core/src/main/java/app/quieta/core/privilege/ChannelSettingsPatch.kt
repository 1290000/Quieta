package app.quieta.core.privilege

/**
 * One channel settings write. Null fields mean "leave unchanged".
 * Used by channel-snapshot import (importance + sound + vibration + lockscreen).
 */
data class ChannelSettingsPatch(
    val packageName: String,
    val channelId: String,
    val importance: Int? = null,
    val soundEnabled: Boolean? = null,
    val vibrationEnabled: Boolean? = null,
    val lockscreenHidden: Boolean? = null,
) {
    val hasExtras: Boolean
        get() = soundEnabled != null || vibrationEnabled != null || lockscreenHidden != null
}
