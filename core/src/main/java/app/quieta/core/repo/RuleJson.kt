package app.quieta.core.repo

import app.quieta.core.model.Rule
import app.quieta.core.model.RuleAction
import org.json.JSONArray
import org.json.JSONObject

object RuleJson {
    const val SCHEMA_VERSION = 1

    fun encode(rules: List<Rule>): String {
        val root = JSONObject()
        root.put("schemaVersion", SCHEMA_VERSION)
        val arr = JSONArray()
        rules.forEach { rule ->
            arr.put(
                JSONObject()
                    .put("id", rule.id)
                    .put("enabled", rule.enabled)
                    .put("packageName", rule.packageName)
                    .put("nameContains", rule.nameContains)
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
                val actionName = o.getString("action")
                add(
                    Rule(
                        id = o.getString("id"),
                        enabled = o.optBoolean("enabled", true),
                        packageName = o.optString("packageName").takeIf { it.isNotEmpty() },
                        nameContains = o.optString("nameContains").takeIf { it.isNotEmpty() },
                        action = RuleAction.valueOf(actionName),
                    ),
                )
            }
        }
    }
}
