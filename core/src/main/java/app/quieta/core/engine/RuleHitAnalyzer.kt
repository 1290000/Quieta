package app.quieta.core.engine

import app.quieta.core.model.AppChannels
import app.quieta.core.model.Rule

data class RuleHitSample(
    val appLabel: String,
    val channelName: String,
    val channelId: String,
)

data class RuleHitStat(
    val ruleId: String,
    /** Channels whose name/id/pkg matches this rule (even if another rule wins). */
    val matchCount: Int,
    /** Channels for which this rule is the final decision winner. */
    val effectiveCount: Int,
    val samples: List<RuleHitSample>,
    val broadKeyword: Boolean,
)

/**
 * Keywords that match far too many channels in the wild.
 * Used only for UI warnings — never auto-mutes.
 */
object BroadKeywords {
    val tokens: Set<String> = setOf(
        "消息", "通知", "服务", "更新", "推送", "提醒", "活动", "资讯",
        "默认", "其他", "misc", "default", "notification", "notice",
        "service", "update", "push", "news", "info", "alert",
    )

    fun isBroad(keyword: String?): Boolean {
        val needle = keyword?.trim()?.lowercase().orEmpty()
        if (needle.length < 2) return false
        return tokens.any { needle == it || needle.contains(it) || it.contains(needle) }
    }
}

object RuleHitAnalyzer {

    private const val SAMPLE_LIMIT = 3

    fun analyze(rules: List<Rule>, apps: List<AppChannels>): Map<String, RuleHitStat> {
        if (rules.isEmpty() || apps.isEmpty()) return emptyMap()
        val engine = RulesEngine(rules)
        val channels = apps.flatMap { app ->
            app.channels.map { ch -> Triple(app.appLabel, ch, engine.decisionFor(ch).matchedRule?.id) }
        }
        return rules.associate { rule ->
            val matched = channels.filter { (_, ch, _) -> rule.matches(ch) }
            val effective = matched.filter { (_, _, winner) -> winner == rule.id }
            rule.id to RuleHitStat(
                ruleId = rule.id,
                matchCount = matched.size,
                effectiveCount = effective.size,
                samples = effective.ifEmpty { matched }.take(SAMPLE_LIMIT).map { (label, ch, _) ->
                    RuleHitSample(label, ch.name, ch.id)
                },
                broadKeyword = BroadKeywords.isBroad(rule.nameContains) ||
                    BroadKeywords.isBroad(rule.channelIdPrefix) ||
                    BroadKeywords.isBroad(rule.packagePrefix),
            )
        }
    }
}
