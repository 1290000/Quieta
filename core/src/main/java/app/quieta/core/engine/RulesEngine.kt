package app.quieta.core.engine

import app.quieta.core.model.Channel
import app.quieta.core.model.Rule
import app.quieta.core.model.RuleAction

data class RuleDecision(
    val action: RuleAction,
    val matchedRule: Rule?,
    val reason: String,
)

/**
 * Pure matcher.
 * 1. Collect enabled matches.
 * 2. Any KEEP match → KEEP (whitelist wins).
 * 3. Else highest specificity non-KEEP; ties break by list order.
 * 4. No match → KEEP.
 */
class RulesEngine(private val rules: List<Rule>) {

    fun decisionFor(channel: Channel): RuleDecision {
        val matches = rules.filter { it.matches(channel) }
        if (matches.isEmpty()) {
            return RuleDecision(RuleAction.KEEP, null, "无匹配规则")
        }
        val keep = matches.firstOrNull { it.action == RuleAction.KEEP }
        if (keep != null) {
            return RuleDecision(RuleAction.KEEP, keep, "白名单：${describe(keep)}")
        }
        val best = matches.sortedByDescending { it.specificity }.first()
        return RuleDecision(best.action, best, "命中：${describe(best)}")
    }

    fun actionFor(channel: Channel): RuleAction = decisionFor(channel).action

    fun plan(channels: List<Channel>): Map<Channel, RuleAction> =
        channels.associateWith { actionFor(it) }

    fun decisions(channels: List<Channel>): Map<Channel, RuleDecision> =
        channels.associateWith { decisionFor(it) }

    private fun describe(rule: Rule): String = buildString {
        rule.channelIdExact?.let { append("id=${normalizeIdList(it).joinToString("/")} ") }
        rule.channelIdPrefix?.let { append("id前缀=$it ") }
        rule.packageName?.let { append("包名=${normalizeIdList(it).joinToString("/")} ") }
        rule.packagePrefix?.let { append("包前缀=$it ") }
        rule.nameContains?.let {
            val scope = when {
                rule.matchName && rule.matchId -> "名称或id"
                rule.matchName -> "名称"
                rule.matchId -> "id"
                else -> "关键词"
            }
            append("${scope}含“$it”")
        }
    }.trim().ifEmpty { rule.id }
}

/** Split multi-value exact fields: `,` `，` newline. */
fun normalizeIdList(raw: String?): List<String> =
    raw?.split(',', '，', '\n', '\r')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

fun Rule.matches(channel: Channel): Boolean {
    if (!enabled) return false
    if (!hasAnyFilter) return false

    val pkgExactList = normalizeIdList(packageName)
    if (pkgExactList.isNotEmpty() &&
        pkgExactList.none { it.equals(channel.packageName, ignoreCase = true) }
    ) {
        return false
    }

    val pkgPrefix = packagePrefix?.trim().orEmpty()
    if (pkgPrefix.isNotEmpty() && !channel.packageName.startsWith(pkgPrefix, ignoreCase = true)) return false

    val idExactList = normalizeIdList(channelIdExact)
    if (idExactList.isNotEmpty() &&
        idExactList.none { it.equals(channel.id, ignoreCase = true) }
    ) {
        return false
    }

    val idPrefix = channelIdPrefix?.trim().orEmpty()
    if (idPrefix.isNotEmpty() && !channel.id.startsWith(idPrefix, ignoreCase = true)) return false

    val needle = nameContains?.trim().orEmpty()
    if (needle.isNotEmpty()) {
        val hitName = matchName && channel.name.contains(needle, ignoreCase = true)
        val hitId = matchId && channel.id.contains(needle, ignoreCase = true)
        if (!hitName && !hitId) return false
    }
    return true
}
