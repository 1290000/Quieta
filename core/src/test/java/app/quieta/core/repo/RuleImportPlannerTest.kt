package app.quieta.core.repo

import app.quieta.core.model.Rule
import app.quieta.core.model.RuleAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleImportPlannerTest {

    private val existing = listOf(
        Rule(id = "a", nameContains = "推广", action = RuleAction.MUTE),
        Rule(id = "b", nameContains = "客服", action = RuleAction.KEEP),
    )

    @Test
    fun `snapshot counts merge candidates`() {
        val incoming = listOf(
            Rule(id = "b", nameContains = "客服改", action = RuleAction.KEEP),
            Rule(id = "c", nameContains = "促销", action = RuleAction.MUTE),
            Rule(id = "d", nameContains = "活动", action = RuleAction.DOWNGRADE),
        )
        val snap = RuleImportPlanner.snapshot(
            existingIds = existing.map { it.id }.toSet(),
            existingCount = existing.size,
            incoming = incoming,
        )
        assertEquals(2, snap.addCount)
        assertEquals(1, snap.skipCount)
        assertEquals(2, snap.keepCount)
        assertEquals(listOf("c", "d"), snap.pending.map { it.id })
        assertEquals(3, snap.incoming.size)
    }

    @Test
    fun `merge appends only new ids and keeps order`() {
        val incoming = listOf(
            Rule(id = "c", nameContains = "新", action = RuleAction.MUTE),
            Rule(id = "a", nameContains = "冲突", action = RuleAction.DOWNGRADE),
        )
        val merged = RuleImportPlanner.merge(existing, incoming)
        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
        // Same-id incoming is skipped, not overwritten.
        assertEquals("推广", merged.first { it.id == "a" }.nameContains)
    }

    @Test
    fun `subset export decodes with kind metadata ignored`() {
        val subset = listOf(existing.first())
        val raw = RuleJson.encode(subset, exportedAt = "2026-09-01T00:00:00Z")
        assertTrue(raw.contains("\"kind\""))
        assertTrue(raw.contains("\"exportedAt\""))
        val decoded = RuleJson.decode(raw)
        assertEquals(subset, decoded)
    }

    @Test
    fun `encode without exportedAt still decodes`() {
        val decoded = RuleJson.decode(RuleJson.encode(existing))
        assertEquals(existing, decoded)
    }
}
