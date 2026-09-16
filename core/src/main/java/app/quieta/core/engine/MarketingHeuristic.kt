package app.quieta.core.engine

import app.quieta.core.model.Channel
import app.quieta.core.model.ChannelImportance

/**
 * Suggest-only heuristics. Never auto-mutes — UI may show a "疑似营销" tag.
 * Requires HIGH/DEFAULT importance + marketing-ish name/id on a non-system app.
 */
object MarketingHeuristic {

    private val strongTokens = listOf(
        "promo", "marketing", "ad", "ads", "advert", "campaign",
        "推广", "促销", "营销", "广告", "优惠", "活动", "折扣", "领券", "种草",
    )

    private val weakTokens = listOf(
        "push", "news", "feed", "promo", "推荐", "资讯", "热点",
    )

    fun isLikelyMarketing(
        channel: Channel,
        isSystemApp: Boolean = false,
    ): Boolean {
        if (isSystemApp) return false
        if (channel.importance == ChannelImportance.NONE ||
            channel.importance == ChannelImportance.MIN
        ) {
            return false
        }
        val haystack = (channel.name + " " + channel.id).lowercase()
        if (strongTokens.any { haystack.contains(it) }) return true
        // Weak tokens only count on visible DEFAULT/HIGH channels.
        val visible = channel.importance == ChannelImportance.DEFAULT ||
            channel.importance == ChannelImportance.HIGH
        return visible && weakTokens.any { haystack.contains(it) }
    }
}
