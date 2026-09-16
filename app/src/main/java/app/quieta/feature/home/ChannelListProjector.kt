package app.quieta.feature.home

import app.quieta.core.engine.MarketingHeuristic
import app.quieta.core.model.AppChannels
import app.quieta.core.model.Channel
import app.quieta.core.model.ChannelImportance
import app.quieta.core.model.RuleAction

enum class ChannelSort {
    CHANNEL_COUNT,
    NAME,
    PACKAGE,
    MAX_IMPORTANCE,
}

enum class SoundFilter {
    ALL,
    ON,
    OFF,
}

data class ChannelListFilters(
    val hasHigh: Boolean = false,
    val hasNone: Boolean = false,
    val willMute: Boolean = false,
    val onlyUser: Boolean = false,
    val onlySystem: Boolean = false,
    val onlyLikelyMarketing: Boolean = false,
    val sound: SoundFilter = SoundFilter.ALL,
) {
    val isActive: Boolean
        get() = hasHigh || hasNone || willMute || onlyUser || onlySystem ||
            onlyLikelyMarketing || sound != SoundFilter.ALL
}

data class ChannelListAppItem(
    val app: AppChannels,
    val channels: List<Channel>,
    val expanded: Boolean,
    /** Suggest-only tags; never auto-applied. */
    val likelyMarketingChannelIds: Set<String> = emptySet(),
)

/** Pure list projection: search → filter → sort → collapse. No IO. */
object ChannelListProjector {

    fun project(
        apps: List<AppChannels>,
        query: String,
        filters: ChannelListFilters,
        sort: ChannelSort,
        plan: Map<Channel, RuleAction>,
        expandedPackages: Set<String>,
        autoExpandOnSearch: Boolean = true,
    ): List<ChannelListAppItem> {
        val needle = query.trim()
        val searched = if (needle.isEmpty()) {
            apps
        } else {
            apps.mapNotNull { app -> filterChannelsForSearch(app, needle) }
        }
        val filtered = searched
            .map { app -> applySoundChannelFilter(app, filters.sound) }
            .filter { app -> app.channels.isNotEmpty() && matchesFilters(app, filters, plan) }
        val sorted = sortApps(filtered, sort)
        return sorted.map { app ->
            val expand = expandedPackages.contains(app.packageName) ||
                (autoExpandOnSearch && needle.isNotEmpty())
            val marketingIds = app.channels
                .filter { ch -> MarketingHeuristic.isLikelyMarketing(ch, app.isSystem) }
                .map { it.id }
                .toSet()
            ChannelListAppItem(
                app = app,
                channels = app.channels,
                expanded = expand,
                likelyMarketingChannelIds = marketingIds,
            )
        }
    }

    private fun applySoundChannelFilter(app: AppChannels, sound: SoundFilter): AppChannels {
        if (sound == SoundFilter.ALL) return app
        val kept = app.channels.filter { ch ->
            if (sound == SoundFilter.ON) ch.soundEnabled else !ch.soundEnabled
        }
        return app.copy(channels = kept)
    }

    fun matchesQuery(app: AppChannels, query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return filterChannelsForSearch(app, needle) != null
    }

    private fun filterChannelsForSearch(app: AppChannels, needle: String): AppChannels? {
        val appHit = app.appLabel.contains(needle, ignoreCase = true) ||
            app.packageName.contains(needle, ignoreCase = true)
        if (appHit) return app
        val channelHits = app.channels.filter { channel ->
            channel.name.contains(needle, ignoreCase = true) ||
                channel.id.contains(needle, ignoreCase = true)
        }
        if (channelHits.isEmpty()) return null
        return app.copy(channels = channelHits)
    }

    private fun matchesFilters(
        app: AppChannels,
        filters: ChannelListFilters,
        plan: Map<Channel, RuleAction>,
    ): Boolean {
        if (!filters.isActive) return true
        if (filters.onlyUser && app.isSystem) return false
        if (filters.onlySystem && !app.isSystem) return false
        if (filters.hasHigh && app.channels.none { it.importance == ChannelImportance.HIGH }) return false
        if (filters.hasNone && app.channels.none { it.importance == ChannelImportance.NONE }) return false
        if (filters.willMute && app.channels.none { plan[it] == RuleAction.MUTE }) return false
        if (filters.onlyLikelyMarketing) {
            val hit = app.channels.any {
                MarketingHeuristic.isLikelyMarketing(it, app.isSystem)
            }
            if (!hit) return false
        }
        if (filters.sound != SoundFilter.ALL) {
            val hit = app.channels.any {
                if (filters.sound == SoundFilter.ON) it.soundEnabled else !it.soundEnabled
            }
            if (!hit) return false
        }
        return true
    }

    private fun sortApps(apps: List<AppChannels>, sort: ChannelSort): List<AppChannels> {
        return when (sort) {
            ChannelSort.CHANNEL_COUNT ->
                apps.sortedWith(compareByDescending<AppChannels> { it.channels.size }.thenBy { it.appLabel.lowercase() })
            ChannelSort.NAME ->
                apps.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.packageName }))
            ChannelSort.PACKAGE ->
                apps.sortedBy { it.packageName }
            ChannelSort.MAX_IMPORTANCE ->
                apps.sortedWith(
                    compareByDescending<AppChannels> { maxImportanceRank(it) }
                        .thenByDescending { it.channels.size },
                )
        }
    }

    private fun maxImportanceRank(app: AppChannels): Int =
        app.channels.maxOfOrNull { importanceRank(it.importance) } ?: 0

    private fun importanceRank(importance: ChannelImportance): Int = when (importance) {
        ChannelImportance.NONE -> 0
        ChannelImportance.MIN -> 1
        ChannelImportance.LOW -> 2
        ChannelImportance.DEFAULT -> 3
        ChannelImportance.HIGH -> 4
    }
}
