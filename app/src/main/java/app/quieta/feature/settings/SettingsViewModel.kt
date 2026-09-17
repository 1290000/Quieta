package app.quieta.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quieta.core.settings.AppSettings
import app.quieta.core.settings.PaletteStyle
import app.quieta.core.settings.ThemeColorSpec
import app.quieta.core.settings.PredictiveBackAnimation
import app.quieta.core.settings.PredictiveBackExitDirection
import app.quieta.core.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = AppSettings(application)

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
}
