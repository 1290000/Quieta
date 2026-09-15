package app.quieta.core.privilege.root

import android.util.Log
import app.quieta.core.model.Channel
import app.quieta.core.model.ChannelImportance
import app.quieta.core.model.PrivilegeId
import app.quieta.core.privilege.PrivilegeBackend
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Root (Magisk / KernelSU / APatch) backend.
 *
 * - [isAvailable] probes `su -c id` (InstallerX RootMode spirit).
 * - listChannels uses root `dumpsys notification` (no Shizuku required).
 * - setImportance prefers the privileged Shizuku binder when present; otherwise
 *   fails with a clear error (Root shell has no stable channel-write API).
 *
 * Reference: InstallerX Authorizer.Root / AppProcessTerminal.Root
 * https://github.com/wxxsfxyzm/InstallerX-Revived
 */
class RootBackend(
    private val context: android.content.Context? = null,
    private val fallback: PrivilegeBackend? = null,
) : PrivilegeBackend {

    override val id: PrivilegeId = PrivilegeId.ROOT

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        probeSu()
    }

    fun rootImplementationLabel(): String {
        return when {
            File("/data/adb/ksu").exists() || File("/data/adb/ksud").exists() -> "KernelSU"
            File("/data/adb/ap").exists() || File("/data/adb/apd").exists() -> "APatch"
            File("/sbin/.magisk").exists() || File("/data/adb/magisk").exists() -> "Magisk"
            probeSu() -> "su"
            else -> "无"
        }
    }

    private fun probeSu(): Boolean {
        return runCatching {
            val p = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor() == 0 && out.contains("uid=0")
        }.getOrDefault(false)
    }

    override suspend fun listChannels(packageName: String): List<Channel> {
        if (fallback?.isAvailable() == true) {
            val viaPriv = fallback.listChannels(packageName)
            if (viaPriv.isNotEmpty()) return viaPriv
        }
        return withContext(Dispatchers.IO) {
            parseDumpsysChannels(packageName)
        }
    }

    override suspend fun setImportance(
        packageName: String,
        channelId: String,
        importance: Int,
    ) {
        // Prefer binder path (Shizuku / Dhizuku) when present — same INotificationManager write.
        if (fallback != null && fallback.isAvailable()) {
            fallback.setImportance(packageName, channelId, importance)
            return
        }
        error("Root 环境未检测到 Shizuku/Dhizuku 绑定，请使用 Shizuku 或 Dhizuku 执行写入")
    }

    /**
     * Best-effort parse of `dumpsys notification` for one package.
     * Regex is ROM-tolerant; empty list on failure (UI treats as no channels).
     */
    private fun parseDumpsysChannels(packageName: String): List<Channel> {
        return runCatching {
            val dump = execSu("dumpsys notification --noredact")
            if (dump.isBlank()) return emptyList()

            val channels = mutableListOf<Channel>()
            // NotificationChannel{ id=xxx ... importance=3 name=... }  (order varies)
            val channelBlock = Regex(
                """NotificationChannel\{([^}]*)\}""",
                RegexOption.DOT_MATCHES_ALL,
            )
            val idRe = Regex("""\bid=([^\s,}]+)""")
            val impRe = Regex("""\bimportance=(\d+)""")
            val nameRe = Regex("""\bname=([^\s,}]+(?:\s+[^\s,}]+)*)""")

            for (match in channelBlock.findAll(dump)) {
                val body = match.groupValues[1]
                if (!body.contains(packageName)) continue
                val id = idRe.find(body)?.groupValues?.get(1) ?: continue
                val imp = impRe.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 3
                val name = nameRe.find(body)?.groupValues?.get(1)?.trim().orEmpty().ifEmpty { id }
                channels += Channel(
                    packageName = packageName,
                    id = id,
                    name = name,
                    importance = imp.toDomain(),
                )
            }
            if (channels.isEmpty()) {
                // Fallback: grep package section then look for channel ids nearby
                val section = dump.lines()
                    .filter { it.contains(packageName) || it.contains("NotificationChannel") }
                section.forEach { line ->
                    val id = idRe.find(line)?.groupValues?.get(1) ?: return@forEach
                    if (line.contains(packageName) || dump.contains("$packageName") && dump.contains("id=$id")) {
                        val imp = impRe.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 3
                        if (channels.none { it.id == id }) {
                            channels += Channel(packageName, id, id, imp.toDomain())
                        }
                    }
                }
            }
            channels.distinctBy { it.id }
        }.getOrElse {
            Log.w(TAG, "parseDumpsysChannels failed", it)
            emptyList()
        }
    }

    private fun execSu(command: String): String {
        val p = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return out
    }

    private fun Int.toDomain(): ChannelImportance = when {
        this <= 0 -> ChannelImportance.NONE
        this == 1 -> ChannelImportance.MIN
        this == 2 -> ChannelImportance.LOW
        this >= 5 -> ChannelImportance.HIGH
        else -> ChannelImportance.DEFAULT
    }

    companion object {
        private const val TAG = "RootBackend"
    }
}
