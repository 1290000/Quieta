package app.quieta.feature.settings

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quieta.BuildConfig
import app.quieta.core.settings.AppSettings
import app.quieta.util.log.FileLoggingWriter
import app.quieta.util.log.QLog
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdateUiState(
    val checking: Boolean = false,
    val message: String? = null,
    val releaseUrl: String? = null,
)

sealed class AboutUiEvent {
    data class ShowError(val message: String) : AboutUiEvent()
    data class ShareLog(val intent: Intent) : AboutUiEvent()
}

class AboutViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val settings = AppSettings(application)
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AboutUiEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val enableFileLogging: StateFlow<Boolean> = settings.enableFileLogging
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun checkUpdate() {
        viewModelScope.launch {
            _state.update { it.copy(checking = true, message = "检查中…") }
            val result = UpdateChecker.check(BuildConfig.VERSION_NAME)
            _state.update {
                it.copy(
                    checking = false,
                    message = result.message,
                    releaseUrl = result.releaseUrl,
                )
            }
            QLog.i(QLog.TAG_ABOUT, "checkUpdate result=${result.message}")
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun setEnableFileLogging(enabled: Boolean) {
        viewModelScope.launch {
            settings.setEnableFileLogging(enabled)
            QLog.i(QLog.TAG_ABOUT, "fileLogging=$enabled")
        }
    }

    fun shareLog() {
        viewModelScope.launch {
            try {
                val file = QLog.fileSink?.latestLogFile()
                if (file == null || !file.exists() || file.length() == 0L) {
                    QLog.w(QLog.TAG_EXPORT, "no log file to share")
                    _events.emit(AboutUiEvent.ShowError("暂无日志，请先开启存储并复现问题"))
                    return@launch
                }
                val uri = FileProvider.getUriForFile(
                    appContext,
                    appContext.packageName + ".fileprovider",
                    file,
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Quieta log ${file.name}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                QLog.i(QLog.TAG_EXPORT, "share log file=${file.name} bytes=${file.length()}")
                _events.emit(AboutUiEvent.ShareLog(Intent.createChooser(intent, "导出日志")))
            } catch (e: Exception) {
                QLog.e(QLog.TAG_EXPORT, "shareLog failed", e)
                _events.emit(AboutUiEvent.ShowError(e.message ?: "导出失败"))
            }
        }
    }
}
