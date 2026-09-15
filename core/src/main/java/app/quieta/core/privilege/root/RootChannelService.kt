package app.quieta.core.privilege.root

import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import com.topjohnwu.superuser.ipc.RootService
import org.lsposed.hiddenapibypass.HiddenApiBypass

/** Non-daemon root worker. Only the owning application UID can call its narrow API. */
class RootChannelService : RootService() {
    private val channels by lazy { NotificationChannelAccess() }
    private var ownerUid = -1

    override fun onCreate() {
        check(Process.myUid() == 0) { "Service must run as root" }
        ownerUid = applicationInfo.uid
        if (Build.VERSION.SDK_INT >= 28) HiddenApiBypass.addHiddenApiExemptions("L")
    }

    private fun validate(packageName: String, uid: Int) {
        require(packageName.isNotBlank())
        check(uid == packageManager.getPackageUid(packageName, 0)) { "Package UID mismatch" }
        check(uid / 100000 == ownerUid / 100000) { "Cross-user access is not supported" }
    }

    private fun reply(block: Bundle.() -> Unit): Bundle {
        check(Binder.getCallingUid() == ownerUid) { "Caller UID mismatch" }
        val token = Binder.clearCallingIdentity()
        return try {
            Bundle().apply {
                try {
                    block()
                    putBoolean("ok", true)
                } catch (error: Exception) {
                    putBoolean("ok", false)
                    putString("error", (error.cause ?: error).message ?: error.javaClass.simpleName)
                }
            }
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    private val binder = object : IRootChannels.Stub() {
        override fun probe(): Bundle = reply {
            putInt("uid", Process.myUid())
            channels.list(packageName, ownerUid)
            putBoolean("readable", true)
            putBoolean("writeSupported", channels.supportsWrite())
        }

        override fun listChannels(packageName: String, uid: Int, offset: Int): Bundle = reply {
            validate(packageName, uid)
            require(offset >= 0)
            val all = channels.list(packageName, uid)
            // Keep Binder payloads bounded for apps with many channels.
            val page = all.drop(offset).take(16)
            putParcelableArrayList("channels", ArrayList(page))
            putBoolean("more", offset + page.size < all.size)
        }

        override fun setImportance(packageName: String, uid: Int, channelId: String, importance: Int): Bundle = reply {
            validate(packageName, uid)
            channels.setImportance(packageName, uid, channelId, importance)
            putBoolean("verified", true)
        }
    }

    override fun onBind(intent: Intent): IBinder = binder
}
