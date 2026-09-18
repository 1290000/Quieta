package app.quieta.core.repo

import app.quieta.core.model.Rule
import app.quieta.core.model.RuleAction
import org.json.JSONArray
import org.json.JSONObject

object RuleJson {
    const val SCHEMA_VERSION = 2

    fun encode(rules: List<Rule>, exportedAt: String? = null): String {
        val root = JSONObject()
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("kind", "rules")
        if (exportedAt != null) root.put("exportedAt", exportedAt)
        val arr = JSONArray()
        rules.forEach { rule ->
            arr.put(
                JSONObject()
                    .put("id", rule.id)
                    .put("enabled", rule.enabled)
                    .putOpt("packageName", rule.packageName)
                    .putOpt("packagePrefix", rule.packagePrefix)
                    .putOpt("nameContains", rule.nameContains)
                    .putOpt("channelIdExact", rule.channelIdExact)
                    .putOpt("channelIdPrefix", rule.channelIdPrefix)
                    .put("matchName", rule.matchName)
                    .put("matchId", rule.matchId)
                    .put("action", rule.action.name),
            )
        }
        root.put("rules", arr)
        return root.toString(2)
    }

    fun decode(raw: String): List<Rule> {
        val root = JSONObject(raw)
        val version = root.optInt("schemaVersion", 1)
        require(version <= SCHEMA_VERSION) { "Unsupported schemaVersion=$version" }
        val arr = root.getJSONArray("rules")
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    Rule(
                        id = o.getString("id"),
                        enabled = o.optBoolean("enabled", true),
                        packageName = o.optString("packageName").takeIf { it.isNotEmpty() },
                        packagePrefix = o.optString("packagePrefix").takeIf { it.isNotEmpty() },
                        nameContains = o.optString("nameContains").takeIf { it.isNotEmpty() },
                        channelIdExact = o.optString("channelIdExact").takeIf { it.isNotEmpty() },
                        channelIdPrefix = o.optString("channelIdPrefix").takeIf { it.isNotEmpty() },
                        // v1 rules matched both name and id via nameContains.
                        matchName = o.optBoolean("matchName", true),
                        matchId = o.optBoolean("matchId", true),
                        action = RuleAction.valueOf(o.getString("action")),
                    ),
                )
            }
        }
    }
}
