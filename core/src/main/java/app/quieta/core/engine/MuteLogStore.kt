package app.quieta.core.engine

import android.content.Context
import app.quieta.core.repo.AsyncLocalState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class MuteLogEntry(
    /** Stable unique id for Lazy list keys. Never use display text as a key. */
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val detail: String,
    val time: String,
    val tag: String,
)

/**
 * Lightweight local log for mute actions. No notification body content.
 * Process-wide singleton so Home writes and Record reads share one list.
 */
class MuteLogStore private constructor(context: Context) {

    private val file by lazy { File(context.applicationContext.filesDir, "mute_logs.json") }
    private val storage = AsyncLocalState(emptyList(), ::load, ::write)
    val entries: StateFlow<List<MuteLogEntry>> = storage.state

    suspend fun append(entry: MuteLogEntry) = storage.update { previous ->
        (listOf(entry) + previous).take(MAX)
    }

    suspend fun clear() = storage.update { emptyList() }

    private fun write(list: List<MuteLogEntry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("label", it.label)
                    .put("detail", it.detail)
                    .put("time", it.time)
                    .put("tag", it.tag),
            )
        }
        file.writeText(arr.toString())
    }

    private fun load(): List<MuteLogEntry> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            val seen = HashSet<String>()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    var id = o.optString("id")
                    if (id.isEmpty() || !seen.add(id)) {
                        id = UUID.randomUUID().toString()
                        seen.add(id)
                    }
                    add(
                        MuteLogEntry(
                            id = id,
                            label = o.getString("label"),
                            detail = o.getString("detail"),
                            time = o.getString("time"),
                            tag = o.getString("tag"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val MAX = 100

        @Volatile
        private var instance: MuteLogStore? = null

        fun getInstance(context: Context): MuteLogStore {
            return instance ?: synchronized(this) {
                instance ?: MuteLogStore(context.applicationContext).also { instance = it }
            }
        }

        fun now(): String =
            SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.getDefault()).format(Date())
    }
}
