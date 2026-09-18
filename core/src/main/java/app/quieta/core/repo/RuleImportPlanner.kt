package app.quieta.core.repo

import app.quieta.core.model.Rule

/** Pure helpers for rule pack / file import previews (merge vs replace). */
object RuleImportPlanner {

    data class Snapshot(
        val incoming: List<Rule>,
        /** Rules that merge would append (id not yet present). */
        val pending: List<Rule>,
        val addCount: Int,
        val skipCount: Int,
        val keepCount: Int,
    )

    fun snapshot(
        existingIds: Set<String>,
        existingCount: Int,
        incoming: List<Rule>,
    ): Snapshot {
        val pending = incoming.filterNot { it.id in existingIds }
        return Snapshot(
            incoming = incoming,
            pending = pending,
            addCount = pending.size,
            skipCount = incoming.size - pending.size,
            keepCount = existingCount,
        )
    }

    /** Idempotent merge: append only rules whose ids are not already stored. */
    fun merge(current: List<Rule>, incoming: List<Rule>): List<Rule> {
        val existing = current.map { it.id }.toSet()
        return current + incoming.filterNot { it.id in existing }
    }
}
