package app.quieta.feature.settings

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quieta.core.engine.NotificationTimelineStore
import app.quieta.core.repo.ChannelInventoryStore
import app.quieta.core.settings.AppSettings
import app.quieta.core.settings.ListenerFlagStore
import app.quieta.core.settings.PaletteStyle
import app.quieta.core.settings.PredictiveBackAnimation
import app.quieta.core.settings.PredictiveBackExitDirection
import app.quieta.core.settings.ThemeColorSpec
import app.quieta.core.settings.ThemeMode
import app.quieta.service.NotificationListenerAccess
import app.quieta.service.TimelineRecovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SettingsUiEvent {
    data class ShareSnapshot(val intent: Intent) : SettingsUiEvent
    data class ShowMessage(val message: String) : SettingsUiEvent
}

data class TimelineDiagnosticsUiState(
    val listenerEnabled: Boolean = false,
    val listenerConnected: Boolean = false,
    val lastEventAt: Long = 0L,
    val timelineEnabled: Boolean = true,
    val healthCheckEnabled: Boolean = false,
    val keepAliveEnabled: Boolean = false,
    val healthJobScheduled: Boolean = false,
    val keepAliveRunning: Boolean = false,
    val notificationsEnabled: Boolean = false,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = AppSettings(application)
    private val inventory = ChannelInventoryStore.getInstance(application)
    private val timelineStore = NotificationTimelineStore.getInstance(application)

    private val _events = MutableSharedFlow<SettingsUiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SettingsUiEvent> = _events.asSharedFlow()

    private val _timelineDiagnostics = MutableStateFlow(TimelineDiagnosticsUiState())
    val timelineDiagnostics: StateFlow<TimelineDiagnosticsUiState> = _timelineDiagnostics.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    val blurEnabled: StateFlow<Boolean> = settings.blurEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val customColors: StateFlow<Boolean> = settings.customColors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val dynamicColor: StateFlow<Boolean> = settings.dynamicColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val paletteStyle: StateFlow<PaletteStyle> = settings.paletteStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaletteStyle.TonalSpot)

    val predictiveBackAnimation: StateFlow<PredictiveBackAnimation> = settings.predictiveBackAnimation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PredictiveBackAnimation.MIUIX)

    val predictiveBackExitDirection: StateFlow<PredictiveBackExitDirection> = settings.predictiveBackExitDirection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PredictiveBackExitDirection.ALWAYS_RIGHT)

    val themeColorSpec: StateFlow<ThemeColorSpec> = settings.themeColorSpec
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeColorSpec.SPEC_2025)

    val seedColorInt: StateFlow<Int> = settings.seedColorInt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0xFF6750A4.toInt())

    val autoMuteNewChannels: StateFlow<Boolean> = settings.autoMuteNewChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val notificationTimelineEnabled: StateFlow<Boolean> = settings.notificationTimelineEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val timelineHealthCheckEnabled: StateFlow<Boolean> = settings.timelineHealthCheckEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val timelineKeepAliveEnabled: StateFlow<Boolean> = settings.timelineKeepAliveEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        refreshTimelineDiagnostics()
    }

    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { settings.setThemeMode(mode) } }
    fun setBlurEnabled(enabled: Boolean) { viewModelScope.launch { settings.setBlurEnabled(enabled) } }
    fun setCustomColors(enabled: Boolean) { viewModelScope.launch { settings.setCustomColors(enabled) } }
    fun setDynamicColor(enabled: Boolean) { viewModelScope.launch { settings.setDynamicColor(enabled) } }
    fun setPaletteStyle(style: PaletteStyle) { viewModelScope.launch { settings.setPaletteStyle(style) } }
    fun setPredictiveBackAnimation(value: PredictiveBackAnimation) {
        viewModelScope.launch { settings.setPredictiveBackAnimation(value) }
    }
    fun setPredictiveBackExitDirection(value: PredictiveBackExitDirection) {
        viewModelScope.launch { settings.setPredictiveBackExitDirection(value) }
    }

    fun setThemeColorSpec(spec: ThemeColorSpec) {
        viewModelScope.launch { settings.setThemeColorSpec(spec) }
    }

    fun setSeedColorInt(argb: Int) {
        viewModelScope.launch { settings.setSeedColorInt(argb) }
    }

    fun setAutoMuteNewChannels(enabled: Boolean) {
        viewModelScope.launch {
            settings.setAutoMuteNewChannels(enabled)
            applyRecoveryFromSettings()
            refreshTimelineDiagnostics()
        }
    }

    fun setNotificationTimelineEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setNotificationTimelineEnabled(enabled)
            applyRecoveryFromSettings()
            refreshTimelineDiagnostics()
        }
    }

    fun setTimelineHealthCheckEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setTimelineHealthCheckEnabled(enabled)
            applyRecoveryFromSettings()
            val msg = if (enabled) {
                "已开启低频监听健康检查（约 20 分钟一次，无前台通知）"
            } else {
                "已关闭监听健康检查"
            }
            _events.emit(SettingsUiEvent.ShowMessage(msg))
            refreshTimelineDiagnostics()
        }
    }

    fun setTimelineKeepAliveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setTimelineKeepAliveEnabled(enabled)
            applyRecoveryFromSettings()
            if (enabled) {
                NotificationListenerAccess.ensureBound(getApplication(), "keep_alive_on")
                val app = getApplication<Application>()
                val notificationsOn = androidx.core.app.NotificationManagerCompat.from(app).areNotificationsEnabled()
                val listenerOn = NotificationListenerAccess.isEnabled(app)
                val warnings = buildList {
                    if (!listenerOn) add("尚未授予通知使用权")
                    if (!notificationsOn) add("系统通知被关闭，前台通知可能无法显示")
                }
                val base = "已开启后台持续采集：会显示低优先级通知并常驻监听进程，更耗电"
                val message = if (warnings.isEmpty()) {
                    base + "。建议在最近任务中锁定息匣，并将电池设为无限制"
                } else {
                    base + "。" + warnings.joinToString("；")
                }
                _events.emit(SettingsUiEvent.ShowMessage(message))
            } else {
                _events.emit(SettingsUiEvent.ShowMessage("已关闭后台持续采集"))
            }
            refreshTimelineDiagnostics()
        }
    }

    fun rebindListener() {
        val app = getApplication<Application>()
        NotificationListenerAccess.ensureBound(app, "settings_rebind")
        viewModelScope.launch {
            timelineStore.reloadFromDisk()
            refreshTimelineDiagnostics()
        }
    }

    fun refreshTimelineDiagnostics() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                TimelineRecovery.syncFromSettings(app)
            }
            val flags = ListenerFlagStore.readFlags(app)
            val state = ListenerFlagStore.readState(app)
            val keepAliveRunning = flags.keepAliveEnabled &&
                state.keepAliveStartedAt > 0L &&
                TimelineRecovery.isListenerProcessAlive(app)
            _timelineDiagnostics.value = TimelineDiagnosticsUiState(
                listenerEnabled = NotificationListenerAccess.isEnabled(app),
                listenerConnected = NotificationListenerAccess.isConnected(app),
                lastEventAt = NotificationListenerAccess.lastEventAt(app),
                timelineEnabled = flags.timelineEnabled,
                healthCheckEnabled = flags.healthCheckEnabled,
                keepAliveEnabled = flags.keepAliveEnabled,
                healthJobScheduled = TimelineRecovery.isHealthJobScheduled(app),
                keepAliveRunning = keepAliveRunning,
                notificationsEnabled = androidx.core.app.NotificationManagerCompat.from(app)
                    .areNotificationsEnabled(),
            )
        }
    }

    private suspend fun applyRecoveryFromSettings() {
        val app = getApplication<Application>()
        withContext(Dispatchers.IO) {
            TimelineRecovery.syncFromSettings(app)
        }
    }

    /** Export full channel inventory cache as snapshot JSON (cross-device import later). */
    fun exportChannelSnapshot() {
        viewModelScope.launch {
            val apps = inventory.current()
            if (apps.isEmpty()) {
                _events.emit(SettingsUiEvent.ShowMessage("暂无盘点缓存，请先在主页完成渠道盘点"))
                return@launch
            }
            runCatching {
                val intent = withContext(Dispatchers.IO) {
                    app.quieta.feature.backup.ChannelSnapshotExport.buildShareIntent(
                        getApplication(),
                        apps,
                    )
                }
                val summary = app.quieta.feature.backup.ChannelSnapshotExport.summarize(apps)
                _events.emit(SettingsUiEvent.ShowMessage("已导出 $summary（importance 等渠道设置）"))
                _events.emit(SettingsUiEvent.ShareSnapshot(intent))
            }.onFailure { e ->
                _events.emit(SettingsUiEvent.ShowMessage(e.message ?: "导出失败"))
            }
        }
    }
}
