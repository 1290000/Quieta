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

    suspend fun append(packageName: String, channelId: String, timestamp: Long = System.currentTimeMillis()) {
        if (packageName.isBlank() || channelId.isBlank()) return
        storage.update { previous ->
            NotificationTimelineReducer.append(previous, packageName, channelId, timestamp)
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
                    .put("channelId", entry.channelId)
                    .put("timestamp", entry.timestamp)
                    .put("count", entry.count),
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
                    val objectValue = array.getJSONObject(index)
                    val timestamp = objectValue.optLong("timestamp", 0L)
                    if (timestamp < now - NotificationTimelineReducer.RETENTION_MILLIS) continue
                    val packageName = objectValue.optString("packageName")
                    val channelId = objectValue.optString("channelId")
                    if (packageName.isBlank() || channelId.isBlank()) continue
                    var id = objectValue.optString("id")
                    if (id.isBlank() || !seen.add(id)) {
                        id = java.util.UUID.randomUUID().toString()
                        seen.add(id)
                    }
                    add(
                        NotificationTimelineEntry(
                            id = id,
                            packageName = packageName,
                            channelId = channelId,
                            timestamp = timestamp,
                            count = objectValue.optInt("count", 1).coerceAtLeast(1),
                        ),
                    )
                }
            }.sortedByDescending { it.timestamp }.take(NotificationTimelineReducer.MAX_ENTRIES)
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
