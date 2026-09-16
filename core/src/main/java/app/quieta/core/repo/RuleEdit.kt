package app.quieta.core.repo

import app.quieta.core.model.Rule

/** Updates editable fields without changing identity or enabled state. */
fun List<Rule>.editRule(id: String, patch: Rule): List<Rule> {
    require(any { it.id == id }) { "Rule no longer exists" }
    return map { rule ->
        if (rule.id != id) rule
        else rule.copy(
            packageName = patch.packageName,
            packagePrefix = patch.packagePrefix,
            nameContains = patch.nameContains,
            channelIdExact = patch.channelIdExact,
            channelIdPrefix = patch.channelIdPrefix,
            matchName = patch.matchName,
            matchId = patch.matchId,
            action = patch.action,
        )
    }
}
