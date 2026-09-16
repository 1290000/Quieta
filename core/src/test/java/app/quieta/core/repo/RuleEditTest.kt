package app.quieta.core.repo

import app.quieta.core.model.Rule
import app.quieta.core.model.RuleAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RuleEditTest {
    @Test fun preservesIdentityOrderAndEnabledState() {
        val original = listOf(
            Rule(id = "first", nameContains = "one", action = RuleAction.KEEP),
            Rule(id = "edited", enabled = false, packageName = "com.example", action = RuleAction.MUTE),
        )
        val patch = Rule(
            id = "patch",
            nameContains = "promotion",
            packageName = "com.example",
            packagePrefix = "com.example.",
            channelIdPrefix = "promo.",
            matchName = true,
            matchId = false,
            action = RuleAction.DOWNGRADE,
        )
        val updated = original.editRule("edited", patch)
        assertEquals(original.first(), updated.first())
        assertEquals("edited", updated[1].id)
        assertFalse(updated[1].enabled)
        assertEquals("com.example", updated[1].packageName)
        assertEquals("com.example.", updated[1].packagePrefix)
        assertEquals("promotion", updated[1].nameContains)
        assertEquals("promo.", updated[1].channelIdPrefix)
        assertEquals(false, updated[1].matchId)
        assertEquals(RuleAction.DOWNGRADE, updated[1].action)
    }

    @Test fun deletedRuleCannotBeResurrected() {
        assertThrows(IllegalArgumentException::class.java) {
            emptyList<Rule>().editRule(
                "deleted",
                Rule(id = "x", nameContains = "test", action = RuleAction.KEEP),
            )
        }
    }

    @Test fun supportsExistingWildcardRules() {
        val rules = listOf(Rule(id = "all", nameContains = "x", action = RuleAction.KEEP))
        val edited = rules.editRule(
            "all",
            Rule(id = "all", packageName = null, nameContains = null, action = RuleAction.MUTE),
        ).single()
        assertNull(edited.packageName)
        assertNull(edited.nameContains)
    }
}
