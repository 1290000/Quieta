package app.quieta.core.privilege.shizuku

import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import app.quieta.core.model.Channel
import app.quieta.core.model.ChannelImportance
import app.quieta.core.model.PrivilegeId
import app.quieta.core.privilege.PrivilegeBackend
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Reads notification channels of other apps via Shizuku + INotificationManager reflection.
 */
class ShizukuBackend : PrivilegeBackend {

    override val id: PrivilegeId = PrivilegeId.SHIZUKU

    override suspend fun isAvailable(): Boolean {
        return try {
            if (!rikka.shizuku.Shizuku.pingBinder()) return false
            rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun listChannels(packageName: String): List<Channel> {
        return try {
            val channels = queryNotificationChannels(packageName)
            channels.map { raw ->
                Channel(
                    packageName = packageName,
                    id = raw.id,
                    name = raw.name?.toString().orEmpty().ifEmpty { raw.id },
                    importance = raw.importance.toDomain(),
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "listChannels failed for $packageName", t)
            emptyList()
        }
    }

    override suspend fun setImportance(
        packageName: String,
        channelId: String,
        importance: Int,
    ) {
        val nm = notificationManager()
        val getOne = nm.javaClass.methods.firstOrNull {
            it.name == "getNotificationChannelForPackage" && it.parameterTypes.size == 3
        } ?: nm.javaClass.methods.firstOrNull {
            it.name == "getNotificationChannel" && it.parameterTypes.size == 3
        } ?: error("getNotificationChannel* not found")

        val channel = getOne.invoke(nm, packageName, channelId, 0)
            ?: error("channel not found: $packageName/$channelId")

        val setImportance = channel.javaClass.methods.firstOrNull {
            it.name == "setImportance" && it.parameterTypes.size == 1
        } ?: error("NotificationChannel.setImportance not found")
        setImportance.invoke(channel, importance)

        val update = nm.javaClass.methods.firstOrNull {
            it.name == "updateNotificationChannel" && it.parameterTypes.size == 3
        } ?: nm.javaClass.methods.firstOrNull {
            it.name == "updateNotificationChannelForPackage" && it.parameterTypes.size == 3
        } ?: error("updateNotificationChannel* not found")

        // Signature: (String pkg, int uid, NotificationChannel) — uid 0 often means system/shell path.
        val uid = runCatching {
            val pmClass = Class.forName("android.app.ActivityThread")
            // Prefer 1000 (system) via binder identity; fallback 0.
            0
        }.getOrDefault(0)
        update.invoke(nm, packageName, uid, channel)
        Log.d(TAG, "setImportance $packageName/$channelId -> $importance")
    }

    private fun notificationManager(): Any {
        val raw = SystemServiceHelper.getSystemService("notification")
            ?: error("notification service binder is null")
        val binder = ShizukuBinderWrapper(raw)
        val stubClass = Class.forName("android.app.INotificationManager\$Stub")
        val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
        return asInterface.invoke(null, binder) ?: error("INotificationManager is null")
    }

    /**
     * @return NotificationChannel list via hidden INotificationManager.
     */
    private fun queryNotificationChannels(packageName: String): List<android.app.NotificationChannel> {
        val nm = notificationManager()
        val method = nm.javaClass.methods.firstOrNull {
            it.name == "getNotificationChannels" && it.parameterTypes.size == 2
        } ?: error("getNotificationChannels not found")
        val slice = method.invoke(nm, packageName, 0) ?: return emptyList()
        val listMethod = slice.javaClass.getMethod("getList")
        val list = listMethod.invoke(slice) as? List<*> ?: return emptyList()
        return list.filterIsInstance<android.app.NotificationChannel>()
    }

    private fun Int.toDomain(): ChannelImportance = when {
        this <= 0 -> ChannelImportance.NONE
        this == 1 -> ChannelImportance.MIN
        this == 2 -> ChannelImportance.LOW
        this >= 5 -> ChannelImportance.HIGH
        else -> ChannelImportance.DEFAULT
    }

    companion object {
        private const val TAG = "ShizukuBackend"
    }
}
