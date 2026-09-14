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
    fun `first matching rule wins`() {
        val engine = RulesEngine(
            listOf(
                Rule(id = "1", nameContains = "推广", action = RuleAction.DOWNGRADE),
                Rule(id = "2", packageName = "com.example.app", action = RuleAction.MUTE),
            ),
        )
        assertEquals(RuleAction.DOWNGRADE, engine.actionFor(channel()))
    }

    @Test
    fun `match checks channel id as well`() {
        val engine = RulesEngine(
            listOf(Rule(id = "1", nameContains = "promo", action = RuleAction.MUTE)),
        )
        assertEquals(RuleAction.MUTE, engine.actionFor(channel(id = "promo_channel")))
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
