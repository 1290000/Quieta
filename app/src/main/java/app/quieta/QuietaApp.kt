package app.quieta

import android.app.Application
import org.lsposed.hiddenapibypass.HiddenApiBypass

class QuietaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Required for INotificationManager reflection (Shizuku channel inventory).
        runCatching { HiddenApiBypass.addHiddenApiExemptions("") }
    }
}
