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
                    soundEnabled = raw.sound?.toString()?.isNotEmpty() == true,
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
        if (uid == 0) error("uid not found for $packageName")

        // MIUI's getNotificationChannelForPackage overloads often return null.
        // Reuse the list path that already works (getNotificationChannelsForPackage).
        val existing = queryNotificationChannels(packageName).find { it.id == channelId }
            ?: error("channel not found: $packageName/$channelId uid=$uid")

        val setImportance = existing.javaClass.methods.firstOrNull {
            it.name == "setImportance" && it.parameterCount == 1
        } ?: error("setImportance not found")
        setImportance.invoke(existing, importance)

        val updated = invokeUpdateChannel(nm, iface, packageName, uid, existing) ||
            invokeCreateChannel(nm, iface, packageName, uid, existing)
        if (!updated) {
            error("updateNotificationChannelForPackage failed uid=$uid")
        }
        // Binder success alone is not proof — MIUI may ignore create-on-existing updates.
        val actual = queryNotificationChannels(packageName).find { it.id == channelId }?.importance
        if (actual != importance) {
            error("importance mismatch after write: expected=$importance actual=$actual")
        }
        Log.d(TAG, "setImportance $packageName/$channelId -> $importance uid=$uid verified")
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
            if (ok.isSuccess) {
                Log.d(TAG, "createNotificationChannel ok for $pkg uid=$uid")
                return true
            } else {
                Log.w(TAG, "createNotificationChannel failed: ${ok.exceptionOrNull()?.cause ?: ok.exceptionOrNull()}")
            }
        }
        return false
    }

    /**
     * MIUI/AOSP expose several overloads of getNotificationChannelForPackage.
     * Match by parameterTypes — first hit by arity can be the wrong signature.
     */
    private fun invokeGetChannel(
        nm: Any,
        iface: Class<*>,
        pkg: String,
        uid: Int,
        channelId: String,
    ): Any? {
        for (m in iface.methods) {
            if (m.name != "getNotificationChannelForPackage") continue
            val t = m.parameterTypes
            val args: Array<Any?> = when {
                // (String pkg, int uid, String channelId, boolean includeDeleted, int userId) — AOSP
                t.size == 5 &&
                    t[0] == String::class.java && t[1] == Integer.TYPE &&
                    t[2] == String::class.java && t[3] == java.lang.Boolean.TYPE &&
                    t[4] == Integer.TYPE ->
                    arrayOf(pkg, uid, channelId, false, 0)
                // (String pkg, int uid, String channelId, boolean includeDeleted)
                t.size == 4 &&
                    t[0] == String::class.java && t[1] == Integer.TYPE &&
                    t[2] == String::class.java && t[3] == java.lang.Boolean.TYPE ->
                    arrayOf(pkg, uid, channelId, false)
                // (String pkg, int uid, String channelId)
                t.size == 3 &&
                    t[0] == String::class.java && t[1] == Integer.TYPE &&
                    t[2] == String::class.java ->
                    arrayOf(pkg, uid, channelId)
                else -> continue
            }
            val result = runCatching { m.invoke(nm, *args) }.getOrNull()
            if (result != null) return result
        }
        return null
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
                4 -> arrayOf(
                    pkg,
                    uid,
                    channel,
                    if (t[3] == java.lang.Boolean.TYPE) false else 0,
                )
                else -> continue
            }
            val result = runCatching { m.invoke(nm, *args) }
            if (result.isSuccess) {
                return true
            } else {
                Log.w(TAG, "updateNotificationChannelForPackage failed: ${result.exceptionOrNull()?.cause ?: result.exceptionOrNull()}")
            }
        }
        return false
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
