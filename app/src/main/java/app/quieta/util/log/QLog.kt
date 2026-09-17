// SPDX-License-Identifier: GPL-3.0-only
package app.quieta.util.log

import android.util.Log

/**
 * App log facade. Always logs to Logcat; file sink is optional via [LogController].
 * Never log notification titles/bodies — only package/channel ids and outcomes.
 */
object QLog {
    const val TAG_BOOT = "Boot"
    const val TAG_PRIVILEGE = "Privilege"
    const val TAG_INVENTORY = "Inventory"
    const val TAG_TIMELINE = "Timeline"
    const val TAG_MUTE = "Mute"
    const val TAG_RULES = "Rules"
    const val TAG_CRASH = "Crash"
    const val TAG_EXPORT = "Export"
    const val TAG_ABOUT = "About"

    @Volatile
    var fileSink: FileLoggingWriter? = null

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        fileSink?.log(Log.DEBUG, tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        fileSink?.log(Log.INFO, tag, message)
    }

    fun w(tag: String, message: String, t: Throwable? = null) {
        Log.w(tag, message, t)
        fileSink?.log(Log.WARN, tag, message, t)
    }

    fun e(tag: String, message: String, t: Throwable? = null) {
        Log.e(tag, message, t)
        fileSink?.log(Log.ERROR, tag, message, t)
    }

    /** Crash-safe write; used by UncaughtExceptionHandler. */
    fun fatal(message: String, t: Throwable? = null) {
        Log.e(TAG_CRASH, message, t)
        fileSink?.logSync(Log.ERROR, TAG_CRASH, message, t)
    }
}
