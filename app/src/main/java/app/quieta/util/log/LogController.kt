// SPDX-License-Identifier: GPL-3.0-only
package app.quieta.util.log

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Plants/unplants the file sink from the user setting (InstallerX LogController pattern).
 * FileLoggingWriter IO is internal; this only reacts to the preference flow.
 */
class LogController(
    context: Context,
    private val enableLogging: kotlinx.coroutines.flow.Flow<Boolean>,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var writer: FileLoggingWriter? = null

    init {
        scope.launch {
            enableLogging.collectLatest { enabled -> updateState(enabled) }
        }
        installCrashLogger()
    }

    private fun updateState(enabled: Boolean) {
        if (enabled) {
            if (writer == null) {
                val w = FileLoggingWriter(appContext)
                writer = w
                QLog.fileSink = w
                QLog.i(QLog.TAG_BOOT, "file logging enabled")
            }
        } else {
            QLog.fileSink = null
            writer?.release()
            writer = null
            QLog.i(QLog.TAG_BOOT, "file logging disabled")
        }
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                QLog.fatal("FATAL thread=${thread.name}", throwable)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
