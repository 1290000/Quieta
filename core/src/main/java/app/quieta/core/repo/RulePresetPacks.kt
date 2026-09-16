package app.quieta.core.repo

import app.quieta.core.model.Rule
import app.quieta.core.model.RuleAction

/**
 * Built-in rule packs (schemaVersion 2). Import replaces user rules after confirm in UI.
 * Keep conservative; packs are suggestions users can edit after import.
 */
object RulePresetPacks {

    data class Pack(
        val id: String,
        val title: String,
        val description: String,
        val json: String,
    )

    val ecommerce = Pack(
        id = "ecommerce",
        title = "电商营销",
        description = "促销/优惠类渠道静音，订单与客服保留",
        json = """
            {
              "schemaVersion": 2,
              "rules": [
                {"id":"eco-keep-order","enabled":true,"nameContains":"订单","matchName":true,"matchId":true,"action":"KEEP"},
                {"id":"eco-keep-support","enabled":true,"nameContains":"客服","matchName":true,"matchId":true,"action":"KEEP"},
                {"id":"eco-mute-promo","enabled":true,"nameContains":"促销","matchName":true,"matchId":true,"action":"MUTE"},
                {"id":"eco-mute-discount","enabled":true,"nameContains":"优惠","matchName":true,"matchId":true,"action":"MUTE"},
                {"id":"eco-mute-coupon","enabled":true,"nameContains":"领券","matchName":true,"matchId":true,"action":"MUTE"},
                {"id":"eco-downgrade-activity","enabled":true,"nameContains":"活动","matchName":true,"matchId":true,"action":"DOWNGRADE"}
              ]
            }
        """.trimIndent(),
    )

    val travel = Pack(
        id = "travel",
        title = "出行",
        description = "营销降噪，行程/票务/客服保留",
        json = """
            {
              "schemaVersion": 2,
              "rules": [
                {"id":"tr-keep-trip","enabled":true,"nameContains":"行程","matchName":true,"matchId":true,"action":"KEEP"},
                {"id":"tr-keep-ticket","enabled":true,"nameContains":"票","matchName":true,"matchId":true,"action":"KEEP"},
                {"id":"tr-keep-support","enabled":true,"nameContains":"客服","matchName":true,"matchId":true,"action":"KEEP"},
                {"id":"tr-mute-marketing","enabled":true,"nameContains":"营销","matchName":true,"matchId":true,"action":"MUTE"},
                {"id":"tr-mute-promo","enabled":true,"nameContains":"促销","matchName":true,"matchId":true,"action":"MUTE"},
                {"id":"tr-downgrade-promotion","enabled":true,"nameContains":"推广","matchName":true,"matchId":true,"action":"DOWNGRADE"}
              ]
            }
        """.trimIndent(),
    )

    val video = Pack(
        id = "video",
        title = "视频直播",
        description = "开播/营销降级或静音，互动消息保留",
        json = """
            {
              "schemaVersion": 2,
              "rules": [
                {"id":"vd-keep-reply","enabled":true,"nameContains":"回复","matchName":true,"matchId":true,"action":"KEEP"},
                {"id":"vd-keep-comment","enabled":true,"nameContains":"评论","matchName":true,"matchId":true,"action":"KEEP"},
                {"id":"vd-mute-marketing","enabled":true,"nameContains":"营销","matchName":true,"matchId":true,"action":"MUTE"},
                {"id":"vd-mute-live-ad","enabled":true,"nameContains":"开播","matchName":true,"matchId":true,"action":"DOWNGRADE"}
              ]
            }
        """.trimIndent(),
    )

    val all: List<Pack> = listOf(ecommerce, travel, video)

    fun requirePack(id: String): Pack = all.first { it.id == id }

    /** Decode helper so ConfigViewModel can reuse RuleJson without duplicating logic. */
    fun decodeRules(pack: Pack): List<Rule> = RuleJson.decode(pack.json)
}
