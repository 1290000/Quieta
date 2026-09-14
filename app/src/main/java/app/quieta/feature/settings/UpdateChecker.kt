package app.quieta.feature.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateCheckResult(
    val latestTag: String?,
    val releaseUrl: String?,
    val hasUpdate: Boolean,
    val message: String,
)

/**
 * User-initiated check against GitHub Releases. No background polling.
 */
object UpdateChecker {

    private const val LATEST =
        "https://api.github.com/repos/1290000/Quieta/releases/latest"

    suspend fun check(currentVersionName: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(LATEST).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode != 200) {
                return@runCatching UpdateCheckResult(
                    latestTag = null,
                    releaseUrl = null,
                    hasUpdate = false,
                    message = "检查失败 HTTP ${conn.responseCode}",
                )
            }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val json = JSONObject(body)
            val tag = json.optString("tag_name").ifEmpty { json.optString("name") }
            val url = json.optString("html_url")
            val latest = tag.removePrefix("v")
            val current = currentVersionName.removePrefix("v").substringBefore("-")
            val hasUpdate = latest.isNotEmpty() && latest != current && isGreater(latest, current)
            UpdateCheckResult(
                latestTag = tag,
                releaseUrl = url,
                hasUpdate = hasUpdate,
                message = if (hasUpdate) {
                    "发现新版本 $tag"
                } else if (tag.isNotEmpty()) {
                    "已是最新（远端 $tag）"
                } else {
                    "远端暂无发布"
                },
            )
        }.getOrElse { e ->
            UpdateCheckResult(
                latestTag = null,
                releaseUrl = null,
                hasUpdate = false,
                message = "检查失败：${e.message}",
            )
        }
    }

    private fun isGreater(a: String, b: String): Boolean {
        val pa = a.split('.', '-').mapNotNull { it.toIntOrNull() }
        val pb = b.split('.', '-').mapNotNull { it.toIntOrNull() }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
