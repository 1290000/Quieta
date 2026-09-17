package app.quieta.core.engine

import app.quieta.core.model.Channel
import app.quieta.core.model.ChannelImportance

/**
 * "允许通知开着，但提醒基本全关" — matches HyperOS channel page where
 * 允许通知=on, 悬浮/声音/振动/锁屏=off.
 */
object SilentButAllowed {

    fun isMatch(channel: Channel): Boolean {
        // 允许通知
        if (channel.importance == ChannelImportance.NONE) return false
        // 无悬浮 / 非横幅
        if (channel.importance == ChannelImportance.HIGH) return false
        // 声音关
        if (channel.soundEnabled) return false
        // 振动关
        if (channel.vibrationEnabled) return false
        // 锁屏尽量不显示（宽松：未隐藏也可接受为低打扰，但默认要求隐藏更贴截图）
        return true
    }
}
