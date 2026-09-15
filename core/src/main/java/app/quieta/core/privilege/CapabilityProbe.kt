package app.quieta.core.privilege

/**
 * Lightweight capability probe result for UI gating.
 */
data class PrivilegeCapabilities(
    val binderAlive: Boolean,
    val permissionGranted: Boolean,
    val canListChannels: Boolean,
) {
    val usable: Boolean get() = binderAlive && permissionGranted
}

object CapabilityProbe {

    fun probeShizuku(): PrivilegeCapabilities {
        return try {
            val alive = rikka.shizuku.Shizuku.pingBinder()
            val granted = alive &&
                rikka.shizuku.Shizuku.checkSelfPermission() ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            PrivilegeCapabilities(
                binderAlive = alive,
                permissionGranted = granted,
                canListChannels = granted,
            )
        } catch (_: Throwable) {
            PrivilegeCapabilities(false, false, false)
        }
    }

    fun isShizukuInstalled(): Boolean {
        return try {
            rikka.shizuku.Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    fun probeDhizuku(context: android.content.Context? = null): PrivilegeCapabilities {
        return try {
            val backend = app.quieta.core.privilege.dhizuku.DhizukuBackend(context)
            val granted = runCatching {
                kotlinx.coroutines.runBlocking { backend.isAvailable() }
            }.getOrDefault(false)
            PrivilegeCapabilities(
                binderAlive = granted,
                permissionGranted = granted,
                canListChannels = granted,
            )
        } catch (_: Throwable) {
            PrivilegeCapabilities(false, false, false)
        }
    }

    fun probeRoot(context: android.content.Context? = null): Pair<Boolean, String> {
        return try {
            val backend = app.quieta.core.privilege.root.RootBackend(context)
            val available = runCatching {
                kotlinx.coroutines.runBlocking { backend.isAvailable() }
            }.getOrDefault(false)
            available to backend.rootImplementationLabel()
        } catch (_: Throwable) {
            false to "无"
        }
    }
}
