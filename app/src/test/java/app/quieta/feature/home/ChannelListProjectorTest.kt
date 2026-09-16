package app.quieta.feature.home

import app.quieta.core.model.AppChannels
import app.quieta.core.model.Channel
import app.quieta.core.model.ChannelImportance
import app.quieta.core.model.RuleAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelListProjectorTest {

    private fun channel(
        pkg: String,
        id: String,
        name: String,
        importance: ChannelImportance = ChannelImportance.DEFAULT,
    ) = Channel(pkg, id, name, importance)

    private val marketing = AppChannels(
        packageName = "app.quieta.notiflab.debug",
        appLabel = "息匣通知实验室",
        channels = listOf(
            channel("app.quieta.notiflab.debug", "lab.marketing.promo", "营销推送", ChannelImportance.HIGH),
            channel("app.quieta.notiflab.debug", "lab.marketing.event", "活动通知", ChannelImportance.DEFAULT),
            channel("app.quieta.notiflab.debug", "lab.privacy.alert", "隐私提醒", ChannelImportance.NONE),
        ),
    )
    private val browser = AppChannels(
        packageName = "com.android.browser",
        appLabel = "浏览器",
        channels = listOf(
            channel("com.android.browser", "downloads", "正在下载内容", ChannelImportance.LOW),
        ),
    )

    @Test
    fun searchMatchesChannelIdAndFiltersChannels() {
        val items = ChannelListProjector.project(
            apps = listOf(marketing, browser),
            query = "promo",
            filters = ChannelListFilters(),
            sort = ChannelSort.CHANNEL_COUNT,
            plan = emptyMap(),
            expandedPackages = emptySet(),
        )
        assertEquals(1, items.size)
        assertEquals("app.quieta.notiflab.debug", items[0].app.packageName)
        assertEquals(listOf("lab.marketing.promo"), items[0].channels.map { it.id })
        assertTrue(items[0].expanded)
    }

    @Test
    fun filterHasNoneKeepsOnlyMatchingApps() {
        val items = ChannelListProjector.project(
            apps = listOf(marketing, browser),
            query = "",
            filters = ChannelListFilters(hasNone = true),
            sort = ChannelSort.CHANNEL_COUNT,
            plan = emptyMap(),
            expandedPackages = emptySet(),
        )
        assertEquals(listOf("app.quieta.notiflab.debug"), items.map { it.app.packageName })
    }

    @Test
    fun filterWillMuteUsesPlan() {
        val plan = mapOf(
            marketing.channels[0] to RuleAction.MUTE,
        )
        val items = ChannelListProjector.project(
            apps = listOf(marketing, browser),
            query = "",
            filters = ChannelListFilters(willMute = true),
            sort = ChannelSort.CHANNEL_COUNT,
            plan = plan,
            expandedPackages = emptySet(),
        )
        assertEquals(listOf("app.quieta.notiflab.debug"), items.map { it.app.packageName })
    }

    @Test
    fun sortByNameIsStable() {
        val items = ChannelListProjector.project(
            apps = listOf(browser, marketing),
            query = "",
            filters = ChannelListFilters(),
            sort = ChannelSort.NAME,
            plan = emptyMap(),
            expandedPackages = emptySet(),
        )
        assertEquals(listOf("息匣通知实验室", "浏览器"), items.map { it.app.appLabel })
    }

    @Test
    fun defaultCollapseUnlessExpandedOrSearch() {
        val items = ChannelListProjector.project(
            apps = listOf(marketing),
            query = "",
            filters = ChannelListFilters(),
            sort = ChannelSort.CHANNEL_COUNT,
            plan = emptyMap(),
            expandedPackages = emptySet(),
        )
        assertEquals(false, items[0].expanded)
    }
}
