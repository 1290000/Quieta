package app.quieta.feature.settings

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quieta.core.repo.ChannelInventoryStore
import app.quieta.core.settings.AppSettings
import app.quieta.core.settings.PaletteStyle
import app.quieta.core.settings.PredictiveBackAnimation
import app.quieta.core.settings.PredictiveBackExitDirection
import app.quieta.core.settings.ThemeColorSpec
import app.quieta.core.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SettingsUiEvent {
    data class ShareSnapshot(val intent: Intent) : SettingsUiEvent
    data class ShowMessage(val message: String) : SettingsUiEvent
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = AppSettings(application)
    private val inventory = ChannelInventoryStore.getInstance(application)

    private val _events = MutableSharedFlow<SettingsUiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SettingsUiEvent> = _events.asSharedFlow()

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
        viewModelScope.launch { settings.setAutoMuteNewChannels(enabled) }
    }

    fun setNotificationTimelineEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setNotificationTimelineEnabled(enabled) }
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
