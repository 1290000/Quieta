package app.quieta.feature.config

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quieta.core.model.Rule
import app.quieta.core.model.RuleAction
import app.quieta.core.repo.RuleRepository
import app.quieta.core.repo.editRule
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConfigUiState(
    val rules: List<Rule> = emptyList(),
    val message: String? = null,
)

class ConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = RuleRepository.getInstance(application)

    private val _state = MutableStateFlow(ConfigUiState())
    val state: StateFlow<ConfigUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.current()
            repo.rules.collect { rules ->
                _state.update { it.copy(rules = rules) }
            }
        }
    }

    fun saveRule(id: String?, name: String, packageName: String, action: RuleAction, onSaved: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.update { rules ->
                    if (id == null) rules + Rule(id = UUID.randomUUID().toString(),
                        nameContains = name.trim().ifEmpty { null },
                        packageName = packageName.trim().ifEmpty { null }, action = action)
                    else rules.editRule(id, name, packageName, action)
                }
                _state.update { it.copy(message = getApplication<Application>().getString(app.quieta.R.string.rule_saved)) }
                onSaved()
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(message = getApplication<Application>().getString(app.quieta.R.string.rule_save_failed)) }
            }
        }
    }

    fun toggle(id: String) {
        viewModelScope.launch {
            repo.update { rules -> rules.map {
                if (it.id == id) it.copy(enabled = !it.enabled) else it
            } }
        }
    }

    fun remove(id: String) {
        viewModelScope.launch {
            repo.update { rules -> rules.filterNot { it.id == id } }
        }
    }

    fun exportJson(): String = RuleJsonSafe.encode(_state.value.rules)

    fun importJson(raw: String) {
        viewModelScope.launch {
            runCatching {
                val rules = repo.importFrom(raw)
                _state.update { it.copy(message = "已导入 ${rules.size} 条规则") }
            }.onFailure { e ->
                _state.update { it.copy(message = "导入失败：${e.message}") }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}

/** Thin wrapper so UI does not depend on org.json types directly. */
private object RuleJsonSafe {
    fun encode(rules: List<Rule>): String = app.quieta.core.repo.RuleJson.encode(rules)
}
