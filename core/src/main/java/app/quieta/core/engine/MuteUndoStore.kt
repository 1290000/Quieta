package app.quieta.core.engine

import android.content.Context
import app.quieta.core.model.Channel
import app.quieta.core.model.ChannelImportance
import app.quieta.core.repo.AsyncLocalState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class MuteSnapshotEntry(
    val packageName: String,
    val channelId: String,
    val channelName: String,
    /** Importance before the write (Android int 0..5). */
    val previousImportance: Int,
    val newImportance: Int,
)

data class MuteSnapshot(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val time: String,
    val entries: List<MuteSnapshotEntry>,
) {
    val size: Int get() = entries.size
}

/**
 * Stores recent mute batches so the user can roll back the last write set.
 * Process-wide singleton (same rule as RuleRepository / MuteLogStore).
 */
class MuteUndoStore private constructor(context: Context) {

    private val file by lazy { File(context.applicationContext.filesDir, "mute_undo.json") }
    private val storage = AsyncLocalState(emptyList<MuteSnapshot>(), ::load, ::write)
    val snapshots: StateFlow<List<MuteSnapshot>> = storage.state

    suspend fun latest(): MuteSnapshot? = storage.current().firstOrNull()

    suspend fun push(snapshot: MuteSnapshot) = storage.update { previous ->
        (listOf(snapshot) + previous).take(MAX)
    }

    suspend fun drop(id: String) = storage.update { previous ->
        previous.filterNot { it.id == id }
    }

    fun channelFrom(entry: MuteSnapshotEntry): Channel = Channel(
        packageName = entry.packageName,
        id = entry.channelId,
        name = entry.channelName.ifEmpty { entry.channelId },
        importance = when (entry.previousImportance) {
            0 -> ChannelImportance.NONE
            1 -> ChannelImportance.MIN
            2 -> ChannelImportance.LOW
            4, 5 -> ChannelImportance.HIGH
            else -> ChannelImportance.DEFAULT
        },
    )

    private fun write(list: List<MuteSnapshot>) {
        val root = JSONArray()
        list.forEach { snap ->
            val entries = JSONArray()
            snap.entries.forEach { e ->
                entries.put(
                    JSONObject()
                        .put("packageName", e.packageName)
                        .put("channelId", e.channelId)
                        .put("channelName", e.channelName)
                        .put("previousImportance", e.previousImportance)
                        .put("newImportance", e.newImportance),
                )
            }
            root.put(
                JSONObject()
                    .put("id", snap.id)
                    .put("label", snap.label)
                    .put("time", snap.time)
                    .put("entries", entries),
            )
        }
        file.writeText(root.toString())
    }

    private fun load(): List<MuteSnapshot> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val root = JSONArray(file.readText())
            buildList {
                for (i in 0 until root.length()) {
                    val o = root.getJSONObject(i)
                    val arr = o.optJSONArray("entries") ?: JSONArray()
                    val entries = buildList {
                        for (j in 0 until arr.length()) {
                            val e = arr.getJSONObject(j)
                            add(
                                MuteSnapshotEntry(
                                    packageName = e.getString("packageName"),
                                    channelId = e.getString("channelId"),
                                    channelName = e.optString("channelName"),
                                    previousImportance = e.optInt("previousImportance", 3),
                                    newImportance = e.optInt("newImportance", 0),
                                ),
                            )
                        }
                    }
                    add(
                        MuteSnapshot(
                            id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
                            label = o.optString("label").ifEmpty { "静音批次" },
                            time = o.optString("time"),
                            entries = entries,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val MAX = 5

        @Volatile
        private var instance: MuteUndoStore? = null

        fun getInstance(context: Context): MuteUndoStore {
            return instance ?: synchronized(this) {
                instance ?: MuteUndoStore(context.applicationContext).also { instance = it }
            }
        }

        fun now(): String =
            SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.getDefault()).format(Date())

        fun importanceToInt(importance: ChannelImportance): Int = when (importance) {
            ChannelImportance.NONE -> 0
            ChannelImportance.MIN -> 1
            ChannelImportance.LOW -> 2
            ChannelImportance.DEFAULT -> 3
            ChannelImportance.HIGH -> 4
        }
    }
}
