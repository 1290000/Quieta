package app.quieta.core.engine

import app.quieta.core.model.Channel
import app.quieta.core.model.ChannelImportance

/** Channel-level quiet categories (allow-notifications still on). */
enum class QuietMode {
    /** 允许通知开，声音/悬浮/振动关（HyperOS 全关但仍允许）。 */
    SILENT_NO_SOUND,

    /** 允许通知开，有声音，但无悬浮横幅（通常无振动）。 */
    QUIET_WITH_SOUND,
}

/**
 * "允许通知开着" 的安静渠道分类。
 */
object SilentButAllowed {

    fun isMatch(channel: Channel, mode: QuietMode): Boolean {
        // 允许通知
        if (channel.importance == ChannelImportance.NONE) return false
        // 无悬浮 / 非横幅
        if (channel.importance == ChannelImportance.HIGH) return false
        return when (mode) {
            QuietMode.SILENT_NO_SOUND ->
                !channel.soundEnabled && !channel.vibrationEnabled
            QuietMode.QUIET_WITH_SOUND ->
                channel.soundEnabled && !channel.vibrationEnabled
        }
    }

    fun isSilentNoSound(channel: Channel): Boolean = isMatch(channel, QuietMode.SILENT_NO_SOUND)

    fun isQuietWithSound(channel: Channel): Boolean = isMatch(channel, QuietMode.QUIET_WITH_SOUND)
}
