package app.quieta.core.privilege.root

import android.app.NotificationChannel
import android.content.Context
import android.os.Bundle
import app.quieta.core.model.Channel
import app.quieta.core.model.ChannelImportance
import app.quieta.core.model.PrivilegeId
import app.quieta.core.privilege.ChannelSettingsPatch
import app.quieta.core.privilege.NotificationChannelPatch
import app.quieta.core.privilege.PrivilegeBackend
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Independent root Binder backend; no Shizuku/Dhizuku or dumpsys fallback. */
class RootBackend(private val context: Context? = null) : PrivilegeBackend {
    override val id = PrivilegeId.ROOT
    private val client get() = RootServiceClient.getInstance(requireNotNull(context))

    override suspend fun isAvailable(): Boolean = probeCapabilities().readable

    suspend fun probeCapabilities(): RootCapabilities {
        val identity = probeIdentity()
        if (!identity.available) return RootCapabilities(identity)
        return try {
            val result = client.use { checked(it.probe()) }
            check(result.getInt("uid", -1) == 0) { "Worker is not root" }
            RootCapabilities(identity, result.getBoolean("readable"), result.getBoolean("writeSupported"))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            RootCapabilities(identity, error = error.message)
        }
    }

    suspend fun probeIdentity(): RootIdentity = withContext(Dispatchers.IO) {
        // Run inside su: manager APK presence alone is never evidence of root access.
        val command = """
            [ "${'$'}(id -u)" = "0" ] || exit 1
            echo QUIETA_ROOT_OK
            printf 'QUIETA_SU_VERSION=%s\n' "${'$'}(su -v 2>/dev/null)"
            exit 0
        """.trimIndent()
        try {
            val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            try {
                if (!process.waitFor(15, TimeUnit.SECONDS)) RootIdentity(false)
                else RootIdentity.fromProbe(process.exitValue(), process.inputStream.bufferedReader().use { it.readText() })
            } finally {
                process.destroy()
                process.inputStream.close()
            }
        } catch (_: java.io.IOException) {
            RootIdentity(false)
        }
    }

    /** Supplemental installed-manager names, never used to decide privilege availability. */
    fun installedManagers(identity: RootIdentity): List<String> {
        if (!identity.available) return emptyList()
        val candidates = when (identity.manager) {
            "KernelSU", "ReSukiSU", "SukiSU", "KernelSU Next" -> mapOf(
                "com.resukisu.resukisu" to "ReSukiSU", "com.sukisu.ultra" to "SukiSU Ultra",
                "com.rifsxd.ksunext" to "KernelSU Next", "me.weishu.kernelsu" to "KernelSU")
            "Magisk" -> mapOf("com.topjohnwu.magisk" to "Magisk")
            "APatch" -> mapOf("me.bmax.apatch" to "APatch")
            else -> emptyMap()
        }
        return candidates.mapNotNull { (pkg, name) ->
            try {
                context?.packageManager?.getPackageInfo(pkg, 0)?.let { name }
            } catch (_: android.content.pm.PackageManager.NameNotFoundException) { null }
        }
    }

    override suspend fun listChannels(packageName: String): List<Channel> = withContext(Dispatchers.IO) {
        val uid = requireNotNull(context).packageManager.getPackageUid(packageName, 0)
        client.use { service ->
            val channels = mutableListOf<NotificationChannel>()
            do {
                val reply = checked(service.listChannels(packageName, uid, channels.size))
                @Suppress("DEPRECATION")
                val page = reply.getParcelableArrayList<NotificationChannel>("channels")
                    ?: error("Missing root channel response")
                channels.addAll(page)
                val more = reply.getBoolean("more")
                check(!more || page.isNotEmpty()) { "Root channel pagination stalled" }
            } while (more)
            channels.distinctBy { it.id }.map { NotificationChannelPatch.toDomain(packageName, it) }
        }
    }

    override suspend fun setImportance(packageName: String, channelId: String, importance: Int) = withContext(Dispatchers.IO) {
        applyChannelSettings(
            ChannelSettingsPatch(
                packageName = packageName,
                channelId = channelId,
                importance = importance,
            ),
        )
    }

    override suspend fun applyChannelSettings(patch: ChannelSettingsPatch) = withContext(Dispatchers.IO) {
        val uid = requireNotNull(context).packageManager.getPackageUid(patch.packageName, 0)
        val settings = Bundle().apply {
            if (patch.importance != null) {
                putBoolean("hasImportance", true)
                putInt("importance", patch.importance)
            }
            if (patch.soundEnabled != null) {
                putBoolean("hasSound", true)
                putBoolean("soundEnabled", patch.soundEnabled)
            }
            if (patch.vibrationEnabled != null) {
                putBoolean("hasVibration", true)
                putBoolean("vibrationEnabled", patch.vibrationEnabled)
            }
            if (patch.lockscreenHidden != null) {
                putBoolean("hasLockscreen", true)
                putBoolean("lockscreenHidden", patch.lockscreenHidden)
            }
        }
        client.use { service ->
            check(checked(service.applyChannelSettings(patch.packageName, uid, patch.channelId, settings)).getBoolean("verified")) {
                "Root write was not verified"
            }
        }
    }

    private fun checked(reply: Bundle): Bundle {
        check(reply.getBoolean("ok")) { reply.getString("error") ?: "Root operation failed" }
        return reply
    }
}

/** A write method being present is distinct from a successful, verified write. */
data class RootCapabilities(
    val identity: RootIdentity,
    val readable: Boolean = false,
    val writeSupported: Boolean = false,
    val error: String? = null,
)
