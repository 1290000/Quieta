package app.quieta.core.engine

import app.quieta.core.model.AppChannels
import app.quieta.core.model.Channel
import app.quieta.core.model.ChannelImportance
import app.quieta.core.model.Rule
import app.quieta.core.model.RuleAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleHitAnalyzerTest {

    private fun app(label: String, pkg: String, vararg channels: Channel) =
        AppChannels(pkg, label, channels.toList())

    private fun ch(pkg: String, id: String, name: String) =
        Channel(pkg, id, name, ChannelImportance.DEFAULT)

    @Test
    fun `counts matches and effective wins`() {
        val muteWide = Rule(id = "wide", nameContains = "promo", action = RuleAction.MUTE)
        val muteExact = Rule(id = "exact", channelIdExact = "promo", action = RuleAction.MUTE)
        val apps = listOf(
            app("Lab", "a.b", ch("a.b", "promo", "营销"), ch("a.b", "other", "订单")),
        )
        val stats = RuleHitAnalyzer.analyze(listOf(muteWide, muteExact), apps)
        assertEquals(1, stats.getValue("wide").matchCount)
        assertEquals(0, stats.getValue("wide").effectiveCount)
        assertEquals(1, stats.getValue("exact").matchCount)
        assertEquals(1, stats.getValue("exact").effectiveCount)
        assertEquals("营销", stats.getValue("exact").samples.first().channelName)
        assertTrue(stats.getValue("exact").samples.size >= 1)
    }

    @Test
    fun `flags broad keywords`() {
        assertTrue(BroadKeywords.isBroad("消息"))
        assertTrue(BroadKeywords.isBroad("push"))
        assertFalse(BroadKeywords.isBroad("lab.marketing."))
        val rule = Rule(id = "r", nameContains = "通知", action = RuleAction.MUTE)
        val stats = RuleHitAnalyzer.analyze(
            listOf(rule),
            listOf(app("X", "p", ch("p", "n1", "系统通知"))),
        )
        assertTrue(stats.getValue("r").broadKeyword)
    }
}
