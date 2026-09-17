package app.quieta

import android.app.Application
import android.os.Build
import app.quieta.core.settings.AppSettings
import app.quieta.util.log.LogController
import app.quieta.util.log.QLog
import org.lsposed.hiddenapibypass.HiddenApiBypass

class QuietaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Required for INotificationManager reflection (Shizuku channel inventory).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { HiddenApiBypass.addHiddenApiExemptions("") }
        }
        val settings = AppSettings(this)
        LogController(this, settings.enableFileLogging)
        QLog.i(QLog.TAG_BOOT, "app created version=${BuildConfig.VERSION_NAME}")
    }
}
