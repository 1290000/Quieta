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
class ShizukuBackend(
    private val context: android.content.Context? = null,
) : PrivilegeBackend {

    override val id: PrivilegeId = PrivilegeId.SHIZUKU

    private fun uidOf(packageName: String): Int {
        return runCatching {
            context?.packageManager?.getPackageUid(packageName, 0) ?: 0
        }.getOrDefault(0)
    }

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
        val iface = Class.forName("android.app.INotificationManager")
        val uid = uidOf(packageName)

        val getOne = iface.methods.firstOrNull {
            it.name == "getNotificationChannelForPackage" && it.parameterCount == 5
        } ?: iface.methods.firstOrNull {
            it.name == "getNotificationChannelForPackage" && it.parameterCount == 4
        } ?: error("getNotificationChannelForPackage not found")

        val channel = when (getOne.parameterCount) {
            5 -> getOne.invoke(nm, packageName, channelId, uid, false, 0)
            4 -> getOne.invoke(nm, packageName, channelId, uid)
            else -> null
        } ?: error("channel not found: $packageName/$channelId uid=$uid")

        val setImportance = channel.javaClass.methods.firstOrNull {
            it.name == "setImportance" && it.parameterCount == 1
        } ?: error("setImportance not found")
        setImportance.invoke(channel, importance)

        val update = iface.methods.firstOrNull {
            it.name == "updateNotificationChannelForPackage" && it.parameterCount == 3
        } ?: error("updateNotificationChannelForPackage not found")
        update.invoke(nm, packageName, uid, channel)
        Log.d(TAG, "setImportance $packageName/$channelId -> $importance uid=$uid")
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
        val iface = Class.forName("android.app.INotificationManager")

        // Preferred: getNotificationChannelsForPackage(String pkg, int uid, boolean includeDeleted)
        val preferred = iface.methods.firstOrNull {
            it.name == "getNotificationChannelsForPackage" && it.parameterCount == 3
        }
        if (preferred != null) {
            val uid = uidOf(packageName)
            val slice = runCatching {
                preferred.invoke(nm, packageName, uid, false)
            }.onFailure { Log.w(TAG, "preferred invoke failed for $packageName uid=$uid", it) }
                .getOrNull()
            Log.d(TAG, "preferred slice=$slice uid=$uid for $packageName")
            val list = slice?.let {
                runCatching { it.javaClass.getMethod("getList").invoke(it) as? List<*> }.getOrNull()
            }
            val channels = list?.filterIsInstance<android.app.NotificationChannel>().orEmpty()
            if (channels.isNotEmpty()) {
                Log.d(TAG, "preferred -> ${channels.size} for $packageName")
                return channels
            }
        }

        // Fallback: getNotificationChannels(String pkg, int userId) on older signatures
        val fallback = iface.methods.firstOrNull {
            it.name == "getNotificationChannels" && it.parameterCount == 2
        }
        if (fallback != null) {
            val slice = runCatching { fallback.invoke(nm, packageName, 0) }.getOrNull()
            val list = slice?.let {
                runCatching { it.javaClass.getMethod("getList").invoke(it) as? List<*> }.getOrNull()
            }
            val channels = list?.filterIsInstance<android.app.NotificationChannel>().orEmpty()
            if (channels.isNotEmpty()) {
                Log.d(TAG, "fallback -> ${channels.size} for $packageName")
                return channels
            }
        }

        Log.w(TAG, "no channels for $packageName (preferred=${preferred != null})")
        return emptyList()
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
