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
            // pingBinder is enough to detect a running Shizuku/Sui.
            rikka.shizuku.Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }
}
