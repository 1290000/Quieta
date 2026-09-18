package app.quieta

import android.app.Application
import android.os.Build
import app.quieta.service.NotificationListenerAccess
import app.quieta.service.TimelineRecovery
import app.quieta.util.log.LogController
import app.quieta.util.log.QLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass
import app.quieta.core.settings.AppSettings

class QuietaApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Required for INotificationManager reflection (Shizuku channel inventory).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { HiddenApiBypass.addHiddenApiExemptions("") }
        }
        val settings = AppSettings(this)
        LogController(this, settings.enableFileLogging)
        val processName = TimelineRecovery.currentProcessName(this)
        QLog.i(QLog.TAG_BOOT, "app created version=${BuildConfig.VERSION_NAME} process=$processName")
        // HyperOS unbinds NLS after process death; re-request so timeline keeps writing.
        NotificationListenerAccess.ensureBound(this, "app_create")
        if (!TimelineRecovery.isListenerProcess(this)) {
            appScope.launch {
                TimelineRecovery.syncFromSettings(this@QuietaApp)
            }
        }
    }
}
