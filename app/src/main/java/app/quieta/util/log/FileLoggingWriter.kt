// SPDX-License-Identifier: GPL-3.0-only
// Lightweight file logger ported from InstallerX FileLoggingTree (no Timber dep).
package app.quieta.util.log

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

/**
 * Buffered async file logger. Producer never blocks; IO happens on a dedicated scope.
 * Rotation: 4MB / 24h, keep at most 2 files (InstallerX FileLoggingTree limits).
 */
class FileLoggingWriter(private val context: Context) {

    companion object {
        const val LOG_DIR_NAME = "logs"
        const val LOG_SUFFIX = ".log"
        private const val MAX_LOG_FILES = 2
        private const val MAX_FILE_SIZE = 4 * 1024 * 1024L
        private const val MAX_LOG_AGE_MS = 24 * 60 * 60 * 1000L
        private const val CHANNEL_CAPACITY = 1000
        private const val TAG = "FileLoggingWriter"
    }

    private val logDir: File by lazy { File(context.cacheDir, LOG_DIR_NAME) }
    private val entryDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileNameDateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    private val logChannel = Channel<String>(
        capacity = CHANNEL_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentLogFile: File? = null

    init {
        scope.launch {
            ensureDirectoryExists()
            initializeFromExistingFile()
            logChannel.consumeEach { writeToFile(it) }
        }
    }

    fun log(priority: Int, tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = synchronized(entryDateFormat) { entryDateFormat.format(Date()) }
        val builder = StringBuilder()
            .append(timestamp)
            .append(' ').append(priorityChar(priority))
            .append('/').append(tag)
            .append(": ").append(message)
            .append('\n')
        if (throwable != null) {
            builder.append(Log.getStackTraceString(throwable)).append('\n')
        }
        logChannel.trySend(builder.toString())
    }

    /** Synchronous append for crash paths — process may die before Channel drains. */
    fun logSync(priority: Int, tag: String, message: String, throwable: Throwable? = null) {
        try {
            val timestamp = synchronized(entryDateFormat) { entryDateFormat.format(Date()) }
            val builder = StringBuilder()
                .append(timestamp)
                .append(' ').append(priorityChar(priority))
                .append('/').append(tag)
                .append(": ").append(message)
                .append('\n')
            if (throwable != null) {
                builder.append(Log.getStackTraceString(throwable)).append('\n')
            }
            ensureDirectoryExists()
            ensureLogFileReady()
            currentLogFile?.appendText(builder.toString())
        } catch (e: Exception) {
            Log.e(TAG, "sync log failed", e)
        }
    }

    fun latestLogFile(): File? {
        ensureDirectoryExists()
        return logDir.listFiles { _, name -> name.endsWith(LOG_SUFFIX) }
            ?.maxByOrNull { it.lastModified() }
            ?.takeIf { it.length() > 0L }
    }

    fun release() {
        scope.cancel()
        logChannel.close()
    }

    private fun ensureDirectoryExists() {
        if (!logDir.exists()) logDir.mkdirs()
    }

    private fun writeToFile(content: String) {
        try {
            ensureLogFileReady()
            currentLogFile?.appendText(content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log to file", e)
        }
    }

    private fun ensureLogFileReady() {
        val now = System.currentTimeMillis()
        val file = currentLogFile
        var needNewFile = !logDir.exists()
        if (file == null || !file.exists()) {
            needNewFile = true
        } else if (file.length() > MAX_FILE_SIZE || now - file.lastModified() > MAX_LOG_AGE_MS) {
            needNewFile = true
        }
        if (needNewFile) rotateLogs()
    }

    private fun initializeFromExistingFile() {
        try {
            val lastFile = logDir.listFiles { _, name -> name.endsWith(LOG_SUFFIX) }
                ?.maxByOrNull { it.lastModified() } ?: return
            val now = System.currentTimeMillis()
            if (now - lastFile.lastModified() < MAX_LOG_AGE_MS && lastFile.length() < MAX_FILE_SIZE) {
                currentLogFile = lastFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init from existing file", e)
        }
    }

    private fun rotateLogs() {
        createNewLogFile()
        cleanOldLogFiles()
    }

    private fun createNewLogFile() {
        try {
            val fileName = fileNameDateFormat.format(Date()) + LOG_SUFFIX
            val newFile = File(logDir, fileName)
            currentLogFile = if (newFile.exists()) {
                File(logDir, "${fileNameDateFormat.format(Date())}_${System.currentTimeMillis()}$LOG_SUFFIX")
            } else {
                newFile
            }
            currentLogFile?.createNewFile()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create new log file", e)
        }
    }

    private fun cleanOldLogFiles() {
        try {
            val files = logDir.listFiles { _, name -> name.endsWith(LOG_SUFFIX) } ?: return
            if (files.size > MAX_LOG_FILES) {
                files.sortByDescending { it.lastModified() }
                for (i in MAX_LOG_FILES until files.size) {
                    files[i].delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean old logs", e)
        }
    }

    private fun priorityChar(priority: Int) = when (priority) {
        Log.VERBOSE -> 'V'
        Log.DEBUG -> 'D'
        Log.INFO -> 'I'
        Log.WARN -> 'W'
        Log.ERROR -> 'E'
        Log.ASSERT -> 'A'
        else -> '?'
    }
}
