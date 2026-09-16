package app.quieta.feature.config

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quieta.core.engine.RuleHitStat
import app.quieta.core.engine.RuleHitAnalyzer
import app.quieta.core.model.AppChannels
import app.quieta.core.model.Rule
import app.quieta.core.model.RuleAction
import app.quieta.core.repo.ChannelInventoryStore
import app.quieta.core.repo.RuleRepository
import app.quieta.core.repo.editRule
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RuleDraft(
    val nameContains: String = "",
    val packageName: String = "",
    val packagePrefix: String = "",
    val channelIdExact: String = "",
    val channelIdPrefix: String = "",
    val matchName: Boolean = true,
    val matchId: Boolean = true,
    val action: RuleAction = RuleAction.MUTE,
)

data class ConfigUiState(
    val rules: List<Rule> = emptyList(),
    val hitStats: Map<String, RuleHitStat> = emptyMap(),
    val inventoryReady: Boolean = false,
    val message: String? = null,
    val editorOpen: Boolean = false,
    val editingRuleId: String? = null,
    val draft: RuleDraft = RuleDraft(),
)

class ConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = RuleRepository.getInstance(application)
    private val inventory = ChannelInventoryStore.getInstance(application)

    private val _state = MutableStateFlow(ConfigUiState())
    val state: StateFlow<ConfigUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.current()
            combine(repo.rules, inventory.snapshot) { rules, apps ->
                rules to apps
            }.collect { (rules, apps) ->
                val stats = withContext(Dispatchers.Default) {
                    RuleHitAnalyzer.analyze(rules, apps)
                }
                _state.update {
                    it.copy(
                        rules = rules,
                        hitStats = stats,
                        inventoryReady = apps.isNotEmpty(),
                    )
                }
            }
        }
    }

    fun draftOf(rule: Rule): RuleDraft = RuleDraft(
        nameContains = rule.nameContains.orEmpty(),
        packageName = rule.packageName.orEmpty(),
        packagePrefix = rule.packagePrefix.orEmpty(),
        channelIdExact = rule.channelIdExact.orEmpty(),
        channelIdPrefix = rule.channelIdPrefix.orEmpty(),
        matchName = rule.matchName,
        matchId = rule.matchId,
        action = rule.action,
    )

    fun openAddRule() {
        _state.update {
            it.copy(editorOpen = true, editingRuleId = null, draft = RuleDraft(), message = null)
        }
    }

    fun openEditRule(rule: Rule) {
        _state.update {
            it.copy(editorOpen = true, editingRuleId = rule.id, draft = draftOf(rule), message = null)
        }
    }

    fun closeRuleEditor() {
        _state.update { it.copy(editorOpen = false, editingRuleId = null) }
    }

    fun updateDraft(draft: RuleDraft) {
        _state.update { it.copy(draft = draft) }
    }

    fun saveEditor(onSaved: () -> Unit) {
        saveRule(_state.value.editingRuleId, _state.value.draft) {
            _state.update { it.copy(editorOpen = false, editingRuleId = null) }
            onSaved()
        }
    }

    fun saveRule(id: String?, draft: RuleDraft, onSaved: () -> Unit) {
        viewModelScope.launch {
            try {
                val trimmed = Rule(
                    id = id ?: UUID.randomUUID().toString(),
                    nameContains = draft.nameContains.trim().ifEmpty { null },
                    packageName = draft.packageName.trim().ifEmpty { null },
                    packagePrefix = draft.packagePrefix.trim().ifEmpty { null },
                    channelIdExact = draft.channelIdExact.trim().ifEmpty { null },
                    channelIdPrefix = draft.channelIdPrefix.trim().ifEmpty { null },
                    matchName = draft.matchName,
                    matchId = draft.matchId,
                    action = draft.action,
                )
                if (!trimmed.hasAnyFilter && id == null) {
                    _state.update { it.copy(message = "请至少填写一个匹配条件") }
                    return@launch
                }
                repo.update { rules ->
                    if (id == null) rules + trimmed
                    else rules.editRule(id, trimmed)
                }
                _state.update {
                    it.copy(message = getApplication<Application>().getString(app.quieta.R.string.rule_saved))
                }
                onSaved()
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(message = getApplication<Application>().getString(app.quieta.R.string.rule_save_failed))
                }
            }
        }
    }

    fun importPresetPack(packId: String) {
        viewModelScope.launch {
            runCatching {
                val pack = app.quieta.core.repo.RulePresetPacks.requirePack(packId)
                val rules = app.quieta.core.repo.RulePresetPacks.decodeRules(pack)
                repo.replaceAll(rules)
                _state.update { it.copy(message = "已导入规则包「${pack.title}」（${rules.size} 条）") }
            }.onFailure { e ->
                _state.update { it.copy(message = "导入规则包失败：${e.message}") }
            }
        }
    }

    fun toggle(id: String) {
        viewModelScope.launch {
            repo.update { rules ->
                rules.map { if (it.id == id) it.copy(enabled = !it.enabled) else it }
            }
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
