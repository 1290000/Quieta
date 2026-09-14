package app.quieta.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.quieta.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdateUiState(
    val checking: Boolean = false,
    val message: String? = null,
    val releaseUrl: String? = null,
)

class AboutViewModel : ViewModel() {
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

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
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}
