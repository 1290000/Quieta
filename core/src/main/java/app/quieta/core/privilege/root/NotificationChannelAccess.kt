package app.quieta.core.privilege.root

import android.app.NotificationChannel
import android.os.Bundle
import android.os.IBinder
import app.quieta.core.privilege.ChannelSettingsPatch
import app.quieta.core.privilege.NotificationChannelPatch

/** Typed-signature notification API adapter, executed only inside the root process. */
internal class NotificationChannelAccess {
    private val type = Class.forName("android.app.INotificationManager")
    private val manager: Any = run {
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java).invoke(null, "notification") as IBinder
        Class.forName("android.app.INotificationManager\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            ?: error("Notification service unavailable")
    }

    fun list(packageName: String, uid: Int): List<NotificationChannel> {
        val method = type.getMethod("getNotificationChannelsForPackage",
            String::class.java, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        val slice = method.invoke(manager, packageName, uid, false)
            ?: error("Notification service returned no channel list")
        val list = slice.javaClass.getMethod("getList").invoke(slice) as? List<*>
            ?: error("Invalid notification channel list")
        check(list.all { it is NotificationChannel }) { "Unexpected channel data" }
        return list.filterIsInstance<NotificationChannel>()
    }

    private fun writers() = type.methods.filter { method ->
        val p = method.parameterTypes
        method.name in setOf("updateNotificationChannelForPackage", "createNotificationChannel") &&
            p.size in 3..4 && p[0] == String::class.java && p[1] == Int::class.javaPrimitiveType &&
            p[2] == NotificationChannel::class.java &&
            (p.size == 3 || p[3] == Boolean::class.javaPrimitiveType)
    }.sortedBy { if (it.name == "updateNotificationChannelForPackage") 0 else 1 }

    fun supportsWrite(): Boolean = writers().isNotEmpty()

    fun setImportance(packageName: String, uid: Int, channelId: String, importance: Int) {
        applySettings(
            packageName = packageName,
            uid = uid,
            patch = ChannelSettingsPatch(
                packageName = packageName,
                channelId = channelId,
                importance = importance,
            ),
        )
    }

    fun applySettings(packageName: String, uid: Int, patch: ChannelSettingsPatch) {
        if (patch.importance != null) {
            require(patch.importance in 0..5) { "Invalid importance" }
        }
        val original = list(packageName, uid).firstOrNull { it.id == patch.channelId }
            ?: error("Channel no longer exists: ${patch.channelId}")
        NotificationChannelPatch.applyToChannel(original, patch)
        var failure: Throwable? = null
        for (method in writers()) {
            try {
                val args = if (method.parameterCount == 3) arrayOf(packageName, uid, original)
                    else arrayOf(packageName, uid, original, false)
                method.invoke(manager, *args)
                val live = list(packageName, uid).firstOrNull { it.id == patch.channelId }
                    ?: error("Channel disappeared after write: ${patch.channelId}")
                val issues = NotificationChannelPatch.matches(live, patch)
                check(issues.isEmpty()) { issues.joinToString("; ") }
                return
            } catch (error: ReflectiveOperationException) {
                failure = error.cause ?: error
            } catch (error: IllegalStateException) {
                failure = error
            }
        }
        throw IllegalStateException("Channel update could not be verified", failure)
    }

    companion object {
        fun parsePatch(packageName: String, channelId: String, settings: Bundle): ChannelSettingsPatch {
            return ChannelSettingsPatch(
                packageName = packageName,
                channelId = channelId,
                importance = if (settings.getBoolean("hasImportance")) settings.getInt("importance") else null,
                soundEnabled = if (settings.getBoolean("hasSound")) settings.getBoolean("soundEnabled") else null,
                vibrationEnabled = if (settings.getBoolean("hasVibration")) settings.getBoolean("vibrationEnabled") else null,
                lockscreenHidden = if (settings.getBoolean("hasLockscreen")) settings.getBoolean("lockscreenHidden") else null,
            )
        }
    }
}

/** A successful Binder call alone is not proof that the ROM applied a change. */
internal fun verifyImportance(expected: Int, actual: Int?) {
    check(actual == expected) { "Importance mismatch: expected=$expected actual=$actual" }
}
