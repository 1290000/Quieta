package app.quieta.core.engine

import app.quieta.core.model.Channel
import app.quieta.core.model.ChannelImportance
import app.quieta.core.model.Rule
import app.quieta.core.model.RuleAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesEngineTest {

    private fun channel(
        pkg: String = "com.example.app",
        id: String = "promo",
        name: String = "推广",
    ) = Channel(packageName = pkg, id = id, name = name, importance = ChannelImportance.DEFAULT)

    @Test
    fun `empty rules keep channel`() {
        val engine = RulesEngine(emptyList())
        assertEquals(RuleAction.KEEP, engine.actionFor(channel()))
    }

    @Test
    fun `name contains matches channel name case insensitive`() {
        val engine = RulesEngine(
            listOf(Rule(id = "1", nameContains = "推广", action = RuleAction.MUTE)),
        )
        assertEquals(RuleAction.MUTE, engine.actionFor(channel(name = "活动推广")))
    }

    @Test
    fun `package filter excludes other apps`() {
        val engine = RulesEngine(
            listOf(
                Rule(
                    id = "1",
                    packageName = "com.other",
                    nameContains = "promo",
                    action = RuleAction.MUTE,
                ),
            ),
        )
        assertEquals(RuleAction.KEEP, engine.actionFor(channel(pkg = "com.example.app", id = "promo")))
    }

    @Test
    fun `disabled rule is ignored`() {
        val engine = RulesEngine(
            listOf(Rule(id = "1", enabled = false, nameContains = "推广", action = RuleAction.MUTE)),
        )
        assertEquals(RuleAction.KEEP, engine.actionFor(channel()))
    }

    @Test
    fun `keep whitelist wins over mute`() {
        val engine = RulesEngine(
            listOf(
                Rule(id = "mute", nameContains = "推广", action = RuleAction.MUTE),
                Rule(id = "keep", packageName = "com.example.app", nameContains = "订单", action = RuleAction.KEEP),
            ),
        )
        assertEquals(RuleAction.KEEP, engine.actionFor(channel(name = "订单推广")))
    }

    @Test
    fun `higher specificity wins among non keep`() {
        val engine = RulesEngine(
            listOf(
                Rule(id = "wide", nameContains = "promo", action = RuleAction.DOWNGRADE),
                Rule(id = "precise", channelIdExact = "promo", action = RuleAction.MUTE),
            ),
        )
        assertEquals(RuleAction.MUTE, engine.actionFor(channel(id = "promo")))
    }

    @Test
    fun `package prefix matches family`() {
        val engine = RulesEngine(
            listOf(Rule(id = "1", packagePrefix = "com.example.", nameContains = "promo", action = RuleAction.MUTE)),
        )
        assertEquals(RuleAction.MUTE, engine.actionFor(channel(pkg = "com.example.app")))
        assertEquals(RuleAction.KEEP, engine.actionFor(channel(pkg = "com.other.app")))
    }

    @Test
    fun `channel id prefix matches`() {
        val engine = RulesEngine(
            listOf(Rule(id = "1", channelIdPrefix = "lab.marketing.", action = RuleAction.MUTE)),
        )
        assertEquals(RuleAction.MUTE, engine.actionFor(channel(id = "lab.marketing.promo")))
        assertEquals(RuleAction.KEEP, engine.actionFor(channel(id = "lab.order.status")))
    }

    @Test
    fun `match id disabled ignores nameContains on id`() {
        val engine = RulesEngine(
            listOf(Rule(id = "1", nameContains = "promo", matchId = false, matchName = true, action = RuleAction.MUTE)),
        )
        assertEquals(RuleAction.KEEP, engine.actionFor(channel(id = "promo", name = "订单")))
        assertEquals(RuleAction.MUTE, engine.actionFor(channel(id = "other", name = "promo活动")))
    }

    @Test
    fun `rule without filters does not match by design`() {
        val rule = Rule(id = "1", action = RuleAction.MUTE)
        assertFalse(rule.matches(channel()))
    }

    @Test
    fun `plan maps each channel`() {
        val engine = RulesEngine(
            listOf(Rule(id = "1", nameContains = "推广", action = RuleAction.MUTE)),
        )
        val channels = listOf(channel(name = "推广"), channel(name = "订单"))
        val plan = engine.plan(channels)
        assertEquals(RuleAction.MUTE, plan[channels[0]])
        assertEquals(RuleAction.KEEP, plan[channels[1]])
        assertTrue(plan.size == 2)
    }
}
