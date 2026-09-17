package app.quieta.core.engine

import android.content.Context
import app.quieta.core.repo.AsyncLocalState
import java.io.File
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Process-wide local notification summary store. Notification content is never persisted. */
class NotificationTimelineStore private constructor(context: Context) {

    private val file by lazy { File(context.applicationContext.filesDir, "notification_timeline.json") }
    private val storage = AsyncLocalState(emptyList(), ::load, ::write)
    val entries: StateFlow<List<NotificationTimelineEntry>> = storage.state

    suspend fun append(
        packageName: String,
        appLabel: String,
        channelId: String,
        channelName: String,
        importance: Int,
        soundEnabled: Boolean? = null,
        vibrationEnabled: Boolean? = null,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        if (packageName.isBlank() || channelId.isBlank()) return
        storage.update { previous ->
            NotificationTimelineReducer.append(
                previous = previous,
                packageName = packageName,
                appLabel = appLabel,
                channelId = channelId,
                channelName = channelName,
                timestamp = timestamp,
                importance = importance,
                soundEnabled = soundEnabled,
                vibrationEnabled = vibrationEnabled,
            )
        }
    }

    suspend fun clear() = storage.update { emptyList() }

    private fun write(list: List<NotificationTimelineEntry>) {
        val array = JSONArray()
        list.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("packageName", entry.packageName)
                    .put("appLabel", entry.appLabel)
                    .put("channelId", entry.channelId)
                    .put("channelName", entry.channelName)
                    .put("firstAt", entry.firstAt)
                    .put("lastAt", entry.lastAt)
                    .put("count", entry.count)
                    .put("importance", entry.importance)
                    .putOpt("soundEnabled", entry.soundEnabled)
                    .putOpt("vibrationEnabled", entry.vibrationEnabled),
            )
        }
        file.writeText(array.toString())
    }

    private fun load(): List<NotificationTimelineEntry> {
        if (!file.exists()) return emptyList()
        val now = System.currentTimeMillis()
        return runCatching {
            val array = JSONArray(file.readText())
            val seen = HashSet<String>()
            buildList {
                for (index in 0 until array.length()) {
                    val o = array.getJSONObject(index)
                    val lastAt = o.optLong("lastAt", o.optLong("timestamp", 0L))
                    val firstAt = o.optLong("firstAt", lastAt)
                    if (lastAt < now - NotificationTimelineReducer.RETENTION_MILLIS) continue
                    val packageName = o.optString("packageName")
                    val channelId = o.optString("channelId")
                    if (packageName.isBlank() || channelId.isBlank()) continue
                    var id = o.optString("id")
                    if (id.isBlank() || !seen.add(id)) {
                        id = java.util.UUID.randomUUID().toString()
                        seen.add(id)
                    }
                    add(
                        NotificationTimelineEntry(
                            id = id,
                            packageName = packageName,
                            appLabel = o.optString("appLabel"),
                            channelId = channelId,
                            channelName = o.optString("channelName"),
                            firstAt = firstAt.coerceAtMost(lastAt).coerceAtLeast(0L),
                            lastAt = lastAt,
                            count = o.optInt("count", 1).coerceAtLeast(1),
                            importance = o.optInt("importance", -1),
                            soundEnabled = if (o.has("soundEnabled")) o.optBoolean("soundEnabled") else null,
                            vibrationEnabled = if (o.has("vibrationEnabled")) o.optBoolean("vibrationEnabled") else null,
                        ),
                    )
                }
            }.sortedByDescending { it.lastAt }.take(NotificationTimelineReducer.MAX_ENTRIES)
        }.getOrDefault(emptyList())
    }

    companion object {
        @Volatile
        private var instance: NotificationTimelineStore? = null

        fun getInstance(context: Context): NotificationTimelineStore {
            return instance ?: synchronized(this) {
                instance ?: NotificationTimelineStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
