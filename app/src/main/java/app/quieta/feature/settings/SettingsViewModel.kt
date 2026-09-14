package app.quieta.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quieta.core.settings.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = AppSettings(application)

    val autoMuteNewChannels: StateFlow<Boolean> = settings.autoMuteNewChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setAutoMuteNewChannels(enabled: Boolean) {
        viewModelScope.launch {
            settings.setAutoMuteNewChannels(enabled)
        }
    }
}
