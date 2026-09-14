package app.quieta.core.repo

import app.quieta.core.model.Rule
import app.quieta.core.model.RuleAction

object DefaultRules {
    fun starter(): List<Rule> = listOf(
        Rule(id = "default-mute-marketing", nameContains = "推广", action = RuleAction.MUTE),
        Rule(id = "default-mute-promo", nameContains = "促销", action = RuleAction.MUTE),
        Rule(id = "default-mute-ad", nameContains = "广告", action = RuleAction.MUTE),
        Rule(id = "default-mute-marketing-en", nameContains = "marketing", action = RuleAction.MUTE),
    )
}
