package app.quieta.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quieta.core.settings.AppSettings
import app.quieta.core.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = AppSettings(application)
    val themeMode: StateFlow<ThemeMode> = settings.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { settings.setThemeMode(mode) } }

    val autoMuteNewChannels: StateFlow<Boolean> = settings.autoMuteNewChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val notificationTimelineEnabled: StateFlow<Boolean> = settings.notificationTimelineEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setAutoMuteNewChannels(enabled: Boolean) {
        viewModelScope.launch {
            settings.setAutoMuteNewChannels(enabled)
        }
    }

    fun setNotificationTimelineEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setNotificationTimelineEnabled(enabled)
        }
    }
}
