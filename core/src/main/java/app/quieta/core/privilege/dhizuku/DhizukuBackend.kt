package app.quieta.core.privilege.dhizuku

import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import app.quieta.core.model.Channel
import app.quieta.core.model.ChannelImportance
import app.quieta.core.model.PrivilegeId
import app.quieta.core.privilege.PrivilegeBackend
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import kotlin.coroutines.resume

/**
 * Dhizuku (device-owner) backend.
 * Binder wrapping follows InstallerX DhizukuPermission / DhizukuPrivilegedService.
 * See https://github.com/wxxsfxyzm/InstallerX-Revived and https://github.com/iamr0s/Dhizuku-API
 */
class DhizukuBackend(
    private val context: android.content.Context? = null,
) : PrivilegeBackend {

    override val id: PrivilegeId = PrivilegeId.DHIZUKU

    private fun uidOf(packageName: String): Int {
        return runCatching {
            context?.packageManager?.getPackageUid(packageName, 0) ?: 0
        }.getOrDefault(0)
    }

    override suspend fun isAvailable(): Boolean {
        return try {
            if (!ensureInit()) return false
            Dhizuku.isPermissionGranted()
        } catch (_: Throwable) {
            false
        }
    }

    private fun ensureInit(): Boolean {
        return try {
            Dhizuku.init()
            true
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun requestPermission(): Boolean {
        return suspendCancellableCoroutine { cont ->
            try {
                Dhizuku.init()
                if (Dhizuku.isPermissionGranted()) {
                    cont.resume(true)
                    return@suspendCancellableCoroutine
                }
                Dhizuku.requestPermission(object : DhizukuRequestPermissionListener() {
                    override fun onRequestPermission(grantResult: Int) {
                        if (cont.isActive) {
                            cont.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                        }
                    }
                })
            } catch (t: Throwable) {
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    override suspend fun listChannels(packageName: String): List<Channel> {
        if (!isAvailable()) return emptyList()
        return try {
            queryNotificationChannels(packageName).map { raw ->
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
        if (!isAvailable()) error("Dhizuku 未授权")
        val nm = notificationManager()
        val iface = Class.forName("android.app.INotificationManager")
        val uid = uidOf(packageName)
        if (uid == 0) error("uid not found for $packageName")

        val existing = queryNotificationChannels(packageName).find { it.id == channelId }
            ?: error("channel not found: $packageName/$channelId")

        val setImportance = existing.javaClass.methods.firstOrNull {
            it.name == "setImportance" && it.parameterCount == 1
        } ?: error("setImportance not found")
        setImportance.invoke(existing, importance)

        val updated = invokeUpdateChannel(nm, iface, packageName, uid, existing) ||
            invokeCreateChannel(nm, iface, packageName, uid, existing)
        if (!updated) error("updateNotificationChannelForPackage failed uid=$uid")
        val actual = queryNotificationChannels(packageName).find { it.id == channelId }?.importance
        if (actual != importance) {
            error("importance mismatch after write: expected=$importance actual=$actual")
        }
        Log.d(TAG, "setImportance $packageName/$channelId -> $importance uid=$uid verified")
    }

    /**
     * Dhizuku wraps the system binder (device-owner), same pattern as ShizukuBinderWrapper.
     * InstallerX uses Dhizuku.binderWrapper; we wrap via ShizukuBinderWrapper-compatible path.
     */
    private fun notificationManager(): Any {
        val raw = SystemServiceHelper.getSystemService("notification")
            ?: error("notification service binder is null")
        val binder: IBinder = try {
            Dhizuku.binderWrapper(raw)
        } catch (_: Throwable) {
            // Fallback if Dhizuku wrapper API shape differs
            ShizukuBinderWrapper(raw)
        }
        val stubClass = Class.forName("android.app.INotificationManager\$Stub")
        val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
        return asInterface.invoke(null, binder) ?: error("INotificationManager is null")
    }

    private fun queryNotificationChannels(packageName: String): List<android.app.NotificationChannel> {
        val nm = notificationManager()
        val iface = Class.forName("android.app.INotificationManager")
        val uid = uidOf(packageName)

        val preferred = iface.methods.firstOrNull {
            it.name == "getNotificationChannelsForPackage" && it.parameterCount == 3
        }
        if (preferred != null) {
            val slice = runCatching { preferred.invoke(nm, packageName, uid, false) }.getOrNull()
            val list = slice?.let {
                runCatching { it.javaClass.getMethod("getList").invoke(it) as? List<*> }.getOrNull()
            }
            val channels = list?.filterIsInstance<android.app.NotificationChannel>().orEmpty()
            if (channels.isNotEmpty()) return channels
        }

        val fallback = iface.methods.firstOrNull {
            it.name == "getNotificationChannels" && it.parameterCount == 2
        }
        if (fallback != null) {
            val slice = runCatching { fallback.invoke(nm, packageName, 0) }.getOrNull()
            val list = slice?.let {
                runCatching { it.javaClass.getMethod("getList").invoke(it) as? List<*> }.getOrNull()
            }
            val channels = list?.filterIsInstance<android.app.NotificationChannel>().orEmpty()
            if (channels.isNotEmpty()) return channels
        }
        return emptyList()
    }

    private fun invokeUpdateChannel(
        nm: Any,
        iface: Class<*>,
        pkg: String,
        uid: Int,
        channel: Any,
    ): Boolean {
        for (m in iface.methods) {
            if (m.name != "updateNotificationChannelForPackage") continue
            val t = m.parameterTypes
            val matches = t.size >= 3 &&
                t[0] == String::class.java &&
                t[1] == Integer.TYPE &&
                t[2].isAssignableFrom(channel.javaClass)
            if (!matches) continue
            val args = when (t.size) {
                3 -> arrayOf(pkg, uid, channel)
                4 -> arrayOf(pkg, uid, channel, if (t[3] == java.lang.Boolean.TYPE) false else 0)
                else -> continue
            }
            val result = runCatching { m.invoke(nm, *args) }
            if (result.isSuccess) return true
            Log.w(TAG, "updateNotificationChannelForPackage failed: ${result.exceptionOrNull()?.cause ?: result.exceptionOrNull()}")
        }
        return false
    }

    private fun invokeCreateChannel(
        nm: Any,
        iface: Class<*>,
        pkg: String,
        uid: Int,
        channel: Any,
    ): Boolean {
        for (m in iface.methods) {
            if (m.name != "createNotificationChannel") continue
            val t = m.parameterTypes
            if (t.size < 3) continue
            if (t[0] != String::class.java || t[1] != Integer.TYPE) continue
            val ok = runCatching { m.invoke(nm, pkg, uid, channel) }
            if (ok.isSuccess) return true
        }
        return false
    }

    private fun Int.toDomain(): ChannelImportance = when {
        this <= 0 -> ChannelImportance.NONE
        this == 1 -> ChannelImportance.MIN
        this == 2 -> ChannelImportance.LOW
        this >= 5 -> ChannelImportance.HIGH
        else -> ChannelImportance.DEFAULT
    }

    companion object {
        private const val TAG = "DhizukuBackend"
    }
}
