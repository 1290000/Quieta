package app.quieta.core.engine

import app.quieta.core.model.Channel
import app.quieta.core.model.Rule
import app.quieta.core.model.RuleAction

/**
 * Pure matcher: first enabled matching rule wins; otherwise KEEP.
 */
class RulesEngine(private val rules: List<Rule>) {

    fun actionFor(channel: Channel): RuleAction {
        val rule = rules.firstOrNull { it.matches(channel) } ?: return RuleAction.KEEP
        return rule.action
    }

    fun plan(channels: List<Channel>): Map<Channel, RuleAction> {
        return channels.associateWith { actionFor(it) }
    }
}

fun Rule.matches(channel: Channel): Boolean {
    if (!enabled) return false
    val pkg = packageName
    if (pkg != null && !pkg.equals(channel.packageName, ignoreCase = true)) return false
    val needle = nameContains?.trim().orEmpty()
    if (needle.isNotEmpty()) {
        val hitName = channel.name.contains(needle, ignoreCase = true)
        val hitId = channel.id.contains(needle, ignoreCase = true)
        if (!hitName && !hitId) return false
    }
    // A rule with no filters matches everything.
    return pkg != null || needle.isNotEmpty()
}
