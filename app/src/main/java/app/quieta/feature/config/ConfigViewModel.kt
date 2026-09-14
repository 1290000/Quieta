package app.quieta.feature.config

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quieta.core.model.Rule
import app.quieta.core.model.RuleAction
import app.quieta.core.repo.RuleRepository
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

    private val repo = RuleRepository(application)

    private val _state = MutableStateFlow(ConfigUiState())
    val state: StateFlow<ConfigUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.rules.collect { rules ->
                _state.update { it.copy(rules = rules) }
            }
        }
    }

    fun addNameRule(nameContains: String, action: RuleAction) {
        val needle = nameContains.trim()
        if (needle.isEmpty()) return
        viewModelScope.launch {
            val next = _state.value.rules + Rule(
                id = "r-${System.currentTimeMillis()}",
                nameContains = needle,
                action = action,
            )
            repo.replaceAll(next)
            _state.update { it.copy(message = "已添加规则") }
        }
    }

    fun toggle(id: String) {
        viewModelScope.launch {
            val next = _state.value.rules.map {
                if (it.id == id) it.copy(enabled = !it.enabled) else it
            }
            repo.replaceAll(next)
        }
    }

    fun remove(id: String) {
        viewModelScope.launch {
            repo.replaceAll(_state.value.rules.filterNot { it.id == id })
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
