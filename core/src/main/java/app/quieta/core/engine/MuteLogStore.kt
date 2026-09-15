package app.quieta.core.engine

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
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
 */
class MuteLogStore(context: Context) {

    private val file = File(context.filesDir, "mute_logs.json")
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<MuteLogEntry>> = _entries.asStateFlow()

    suspend fun append(entry: MuteLogEntry) = withContext(Dispatchers.IO) {
        val next = (listOf(entry) + _entries.value).take(MAX)
        _entries.value = next
        write(next)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        _entries.value = emptyList()
        write(emptyList())
    }

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

        fun now(): String =
            SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.getDefault()).format(Date())
    }
}
