package app.quieta.core.repo

import android.content.Context
import app.quieta.core.model.AppChannels
import app.quieta.core.model.Channel
import app.quieta.core.model.ChannelImportance
import java.io.File
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Disk + memory cache of notification-channel inventory.
 *
 * Pattern follows LibChecker (Apache-2.0) LocalAppListRepository /
 * InitializeAppListUseCase: UI reads cache first; full scan is a
 * background refresh that replaces the snapshot in batches.
 *
 * See https://github.com/LibChecker/LibChecker
 */
class ChannelInventoryStore private constructor(context: Context) {

    private val file by lazy { File(context.applicationContext.filesDir, "channel_inventory.json") }
    private val storage = AsyncLocalState(emptyList(), ::loadFromDisk, ::write)
    val snapshot: StateFlow<List<AppChannels>> = storage.state

    suspend fun current(): List<AppChannels> = storage.current()

    suspend fun replaceAll(apps: List<AppChannels>) = storage.update { apps }

    suspend fun upsert(app: AppChannels) = storage.update { previous ->
        previous.filterNot { it.packageName == app.packageName } + app
    }

    suspend fun remove(packageName: String) = storage.update { previous ->
        previous.filterNot { it.packageName == packageName }
    }

    private fun write(list: List<AppChannels>) {
        val arr = JSONArray()
        list.forEach { app ->
            val channels = JSONArray()
            app.channels.forEach { ch ->
                channels.put(
                    JSONObject()
                        .put("id", ch.id)
                        .put("name", ch.name)
                        .put("importance", ch.importance.name),
                )
            }
            arr.put(
                JSONObject()
                    .put("packageName", app.packageName)
                    .put("appLabel", app.appLabel)
                    .put("channels", channels),
            )
        }
        file.writeText(arr.toString())
    }

    private fun loadFromDisk(): List<AppChannels> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val chArr = o.optJSONArray("channels") ?: JSONArray()
                    val channels = buildList {
                        for (j in 0 until chArr.length()) {
                            val c = chArr.getJSONObject(j)
                            add(
                                Channel(
                                    packageName = o.getString("packageName"),
                                    id = c.getString("id"),
                                    name = c.optString("name").ifEmpty { c.getString("id") },
                                    importance = runCatching {
                                        ChannelImportance.valueOf(c.getString("importance"))
                                    }.getOrDefault(ChannelImportance.DEFAULT),
                                ),
                            )
                        }
                    }
                    add(
                        AppChannels(
                            packageName = o.getString("packageName"),
                            appLabel = o.optString("appLabel").ifEmpty { o.getString("packageName") },
                            channels = channels,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        @Volatile
        private var instance: ChannelInventoryStore? = null

        fun getInstance(context: Context): ChannelInventoryStore {
            return instance ?: synchronized(this) {
                instance ?: ChannelInventoryStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
