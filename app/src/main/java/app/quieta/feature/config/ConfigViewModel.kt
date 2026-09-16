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

data class PackImportPreview(
    val packId: String,
    val packTitle: String,
    val addCount: Int,
    val skipCount: Int,
    val keepCount: Int,
    /** Pack rules that will be appended on merge. */
    val pendingRules: List<Rule>,
)

data class ConfigUiState(
    val rules: List<Rule> = emptyList(),
    val hitStats: Map<String, RuleHitStat> = emptyMap(),
    val inventoryReady: Boolean = false,
    val message: String? = null,
    val editorOpen: Boolean = false,
    val editingRuleId: String? = null,
    val draft: RuleDraft = RuleDraft(),
    val packPreview: PackImportPreview? = null,
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

    fun previewPresetPack(packId: String) {
        viewModelScope.launch {
            runCatching {
                val pack = app.quieta.core.repo.RulePresetPacks.requirePack(packId)
                val packRules = app.quieta.core.repo.RulePresetPacks.decodeRules(pack)
                val existingIds = _state.value.rules.map { it.id }.toSet()
                val pending = packRules.filterNot { it.id in existingIds }
                val skip = packRules.size - pending.size
                _state.update {
                    it.copy(
                        packPreview = PackImportPreview(
                            packId = pack.id,
                            packTitle = pack.title,
                            addCount = pending.size,
                            skipCount = skip,
                            keepCount = _state.value.rules.size,
                            pendingRules = pending,
                        ),
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(message = "读取规则包失败：${e.message}") }
            }
        }
    }

    fun dismissPackPreview() {
        _state.update { it.copy(packPreview = null) }
    }

    /** Default: merge — keep all current rules, append only new pack ids. */
    fun confirmMergePresetPack() {
        val preview = _state.value.packPreview ?: return
        viewModelScope.launch {
            runCatching {
                repo.update { current -> current + preview.pendingRules }
                _state.update {
                    it.copy(
                        packPreview = null,
                        message = "已合并导入「${preview.packTitle}」：新增 ${preview.addCount}，跳过 ${preview.skipCount}",
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(message = "合并导入失败：${e.message}") }
            }
        }
    }

    /** Dangerous: wipe user rules and load pack only. UI must confirm twice. */
    fun confirmReplacePresetPack() {
        val preview = _state.value.packPreview ?: return
        viewModelScope.launch {
            runCatching {
                val pack = app.quieta.core.repo.RulePresetPacks.requirePack(preview.packId)
                val rules = app.quieta.core.repo.RulePresetPacks.decodeRules(pack)
                repo.replaceAll(rules)
                _state.update {
                    it.copy(
                        packPreview = null,
                        message = "已替换为规则包「${preview.packTitle}」（${rules.size} 条）",
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(message = "替换失败：${e.message}") }
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
