package app.quieta

import android.app.Application
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

class QuietaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Required for INotificationManager reflection (Shizuku channel inventory).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { HiddenApiBypass.addHiddenApiExemptions("") }
        }
    }
}
