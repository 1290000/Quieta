package app.quieta.core.repo

import app.quieta.core.model.Rule
import app.quieta.core.model.RuleAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleJsonTest {

    @Test
    fun `encode decode roundtrip`() {
        val rules = listOf(
            Rule(id = "1", nameContains = "推广", action = RuleAction.MUTE),
            Rule(
                id = "2",
                packageName = "com.example",
                packagePrefix = "com.example.",
                nameContains = "ad",
                channelIdExact = "exact_id",
                channelIdPrefix = "lab.",
                matchName = true,
                matchId = false,
                action = RuleAction.DOWNGRADE,
                enabled = false,
            ),
        )
        val decoded = RuleJson.decode(RuleJson.encode(rules))
        assertEquals(rules, decoded)
    }

    @Test
    fun `decode includes schemaVersion`() {
        val raw = RuleJson.encode(emptyList())
        assertTrue(raw.contains("schemaVersion"))
        assertEquals(2, RuleJson.SCHEMA_VERSION)
    }

    @Test
    fun `null optional fields survive`() {
        val rule = Rule(id = "only-id", action = RuleAction.KEEP)
        val decoded = RuleJson.decode(RuleJson.encode(listOf(rule))).single()
        assertNull(decoded.packageName)
        assertNull(decoded.nameContains)
        assertNull(decoded.packagePrefix)
        assertNull(decoded.channelIdExact)
        assertNull(decoded.channelIdPrefix)
        assertTrue(decoded.matchName)
        assertTrue(decoded.matchId)
    }

    @Test
    fun `decodes v1 schema`() {
        val v1 = """
            {"schemaVersion":1,"rules":[{"id":"old","enabled":true,"nameContains":"推广","action":"MUTE"}]}
        """.trimIndent()
        val decoded = RuleJson.decode(v1).single()
        assertEquals("推广", decoded.nameContains)
        assertTrue(decoded.matchName)
        assertTrue(decoded.matchId)
        assertNull(decoded.channelIdPrefix)
    }
}
