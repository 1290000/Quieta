package app.quieta.core.repo

import app.quieta.core.model.AppChannels
import app.quieta.core.model.Channel
import app.quieta.core.model.ChannelImportance
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cross-device notification-channel preference snapshot (export format).
 *
 * Keys are always packageName + channel id. Importance is the primary
 * portable field; sound/vibrate/lockscreen are recorded for reference and
 * may not be applied on the target device until backends support them.
 */
object ChannelSnapshotJson {
    const val SCHEMA_VERSION = 1
    const val KIND = "quieta-channel-snapshot"

    data class Source(
        val manufacturer: String? = null,
        val model: String? = null,
    )

    data class Snapshot(
        val apps: List<AppChannels>,
        val exportedAt: String? = null,
        val source: Source? = null,
    )

    fun encode(
        apps: List<AppChannels>,
        exportedAt: String? = null,
        source: Source? = null,
    ): String {
        val root = JSONObject()
        root.put("kind", KIND)
        root.put("schemaVersion", SCHEMA_VERSION)
        if (exportedAt != null) root.put("exportedAt", exportedAt)
        if (source != null) {
            val src = JSONObject()
            source.manufacturer?.let { src.put("manufacturer", it) }
            source.model?.let { src.put("model", it) }
            root.put("source", src)
        }
        val arr = JSONArray()
        apps.forEach { app ->
            val channels = JSONArray()
            app.channels.forEach { ch ->
                channels.put(
                    JSONObject()
                        .put("id", ch.id)
                        .putOpt("name", ch.name)
                        .put("importance", ch.importance.name)
                        .put("soundEnabled", ch.soundEnabled)
                        .put("vibrationEnabled", ch.vibrationEnabled)
                        .put("lockscreenHidden", ch.lockscreenHidden),
                )
            }
            arr.put(
                JSONObject()
                    .put("packageName", app.packageName)
                    .put("appLabel", app.appLabel)
                    .put("isSystem", app.isSystem)
                    .put("channels", channels),
            )
        }
        root.put("apps", arr)
        return root.toString(2)
    }

    fun decode(raw: String): Snapshot {
        val root = JSONObject(raw)
        val kind = root.optString("kind")
        require(kind.isEmpty() || kind == KIND) { "Unsupported kind=$kind" }
        val version = root.optInt("schemaVersion", 1)
        require(version in 1..SCHEMA_VERSION) { "Unsupported schemaVersion=$version" }
        val appsArr = root.optJSONArray("apps") ?: JSONArray()
        val apps = buildList {
            for (i in 0 until appsArr.length()) {
                val o = appsArr.getJSONObject(i)
                val pkg = o.getString("packageName")
                val chArr = o.optJSONArray("channels") ?: JSONArray()
                val channels = buildList {
                    for (j in 0 until chArr.length()) {
                        val c = chArr.getJSONObject(j)
                        val id = c.getString("id")
                        add(
                            Channel(
                                packageName = pkg,
                                id = id,
                                name = c.optString("name").ifEmpty { id },
                                importance = runCatching {
                                    ChannelImportance.valueOf(c.getString("importance"))
                                }.getOrDefault(ChannelImportance.DEFAULT),
                                soundEnabled = c.optBoolean("soundEnabled", true),
                                vibrationEnabled = c.optBoolean("vibrationEnabled", false),
                                lockscreenHidden = c.optBoolean("lockscreenHidden", false),
                            ),
                        )
                    }
                }
                add(
                    AppChannels(
                        packageName = pkg,
                        appLabel = o.optString("appLabel").ifEmpty { pkg },
                        channels = channels,
                        isSystem = o.optBoolean("isSystem", false),
                    ),
                )
            }
        }
        val srcObj = root.optJSONObject("source")
        val source = srcObj?.let {
            Source(
                manufacturer = it.optString("manufacturer").takeIf { v -> v.isNotEmpty() },
                model = it.optString("model").takeIf { v -> v.isNotEmpty() },
            )
        }
        return Snapshot(
            apps = apps,
            exportedAt = root.optString("exportedAt").takeIf { it.isNotEmpty() },
            source = source,
        )
    }

    /**
     * Project inventory for export.
     * [selectedKeys] empty → full inventory; otherwise only `packageName|channelId` hits.
     */
    fun project(apps: List<AppChannels>, selectedKeys: Set<String>): List<AppChannels> {
        if (selectedKeys.isEmpty()) return apps
        return apps.mapNotNull { app ->
            val kept = app.channels.filter { ch ->
                key(app.packageName, ch.id) in selectedKeys
            }
            if (kept.isEmpty()) null else app.copy(channels = kept)
        }
    }

    fun key(packageName: String, channelId: String): String = "$packageName|$channelId"

    fun countChannels(apps: List<AppChannels>): Int = apps.sumOf { it.channels.size }

    fun countApps(apps: List<AppChannels>): Int = apps.size
}
