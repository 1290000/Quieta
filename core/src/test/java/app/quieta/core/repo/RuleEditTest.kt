package app.quieta.core.repo

import app.quieta.core.model.Rule
import app.quieta.core.model.RuleAction
import org.junit.Assert.*
import org.junit.Test

class RuleEditTest {
    @Test fun preservesIdentityOrderAndEnabledState() {
        val original = listOf(Rule(id = "first", nameContains = "one", action = RuleAction.KEEP),
            Rule(id = "edited", enabled = false, packageName = "com.example", action = RuleAction.MUTE))
        val updated = original.editRule("edited", " promotion ", " com.example ", RuleAction.DOWNGRADE)
        assertEquals(original.first(), updated.first())
        assertEquals("edited", updated[1].id)
        assertFalse(updated[1].enabled)
        assertEquals("com.example", updated[1].packageName)
        assertEquals("promotion", updated[1].nameContains)
        assertEquals(RuleAction.DOWNGRADE, updated[1].action)
    }

    @Test fun deletedRuleCannotBeResurrected() {
        assertThrows(IllegalArgumentException::class.java) {
            emptyList<Rule>().editRule("deleted", "test", "", RuleAction.KEEP)
        }
    }

    @Test fun supportsExistingWildcardRules() {
        val rules = listOf(Rule(id = "all", action = RuleAction.KEEP))
        val edited = rules.editRule("all", " ", "", RuleAction.MUTE).single()
        assertNull(edited.packageName)
        assertNull(edited.nameContains)
    }
}
