package app.quieta.core.repo

import app.quieta.core.model.Rule
import app.quieta.core.model.RuleAction

/** Updates editable fields without changing precedence, identity or enabled state. */
fun List<Rule>.editRule(id: String, name: String, packageName: String, action: RuleAction): List<Rule> {
    require(any { it.id == id }) { "Rule no longer exists" }
    return map { rule ->
        if (rule.id == id) rule.copy(nameContains = name.trim().ifEmpty { null },
            packageName = packageName.trim().ifEmpty { null }, action = action) else rule
    }
}
