package app.quieta.feature.config

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quieta.core.engine.RuleHitAnalyzer
import app.quieta.core.engine.RuleHitStat
import app.quieta.core.model.Rule
import app.quieta.core.model.RuleAction
import app.quieta.core.repo.ChannelInventoryStore
import app.quieta.core.repo.RuleImportPlanner
import app.quieta.core.repo.RuleJson
import app.quieta.core.repo.RuleRepository
import app.quieta.core.repo.editRule
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

/** Shared preview for preset packs and user-selected rule JSON files. */
data class RuleImportPreview(
    val title: String,
    val addCount: Int,
    val skipCount: Int,
    val keepCount: Int,
    /** Ids not present locally — what merge will append. */
    val pendingRules: List<Rule>,
    /** Full decoded list — what replace will install. */
    val incomingRules: List<Rule>,
)

data class ConfigUiState(
    val rules: List<Rule> = emptyList(),
    val hitStats: Map<String, RuleHitStat> = emptyMap(),
    val inventoryReady: Boolean = false,
    val message: String? = null,
    val editorOpen: Boolean = false,
    val editingRuleId: String? = null,
    val draft: RuleDraft = RuleDraft(),
    val importPreview: RuleImportPreview? = null,
    val selectionMode: Boolean = false,
    val selectedRuleIds: Set<String> = emptySet(),
)

sealed interface ConfigUiEvent {
    data class ShareRules(val intent: Intent) : ConfigUiEvent
    data class ShowError(val message: String) : ConfigUiEvent
}

class ConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = RuleRepository.getInstance(application)
    private val inventory = ChannelInventoryStore.getInstance(application)

    private val _state = MutableStateFlow(ConfigUiState())
    val state: StateFlow<ConfigUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ConfigUiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ConfigUiEvent> = _events.asSharedFlow()

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
                        selectedRuleIds = it.selectedRuleIds.filter { id -> rules.any { r -> r.id == id } }.toSet(),
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
        exitSelection()
        _state.update {
            it.copy(editorOpen = true, editingRuleId = null, draft = RuleDraft(), message = null)
        }
    }

    fun openEditRule(rule: Rule) {
        exitSelection()
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

    fun enterSelection() {
        _state.update { it.copy(selectionMode = true, selectedRuleIds = emptySet()) }
    }

    fun exitSelection() {
        _state.update { it.copy(selectionMode = false, selectedRuleIds = emptySet()) }
    }

    fun toggleRuleSelection(id: String) {
        _state.update { state ->
            if (!state.selectionMode) state
            else {
                val next = if (id in state.selectedRuleIds) {
                    state.selectedRuleIds - id
                } else {
                    state.selectedRuleIds + id
                }
                state.copy(selectedRuleIds = next)
            }
        }
    }

    fun selectAllRules() {
        _state.update { state ->
            if (!state.selectionMode) state
            else state.copy(selectedRuleIds = state.rules.map { it.id }.toSet())
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedRuleIds = emptySet()) }
    }

    fun previewPresetPack(packId: String) {
        viewModelScope.launch {
            runCatching {
                val pack = app.quieta.core.repo.RulePresetPacks.requirePack(packId)
                val incoming = app.quieta.core.repo.RulePresetPacks.decodeRules(pack)
                presentImportPreview(title = pack.title, incoming = incoming)
            }.onFailure { e ->
                _state.update { it.copy(message = "读取规则包失败：${e.message}") }
            }
        }
    }

    /** Open file-chosen rules JSON (subset export or full backup) into merge/replace dialog. */
    fun previewImportRaw(raw: String, sourceLabel: String = "规则文件") {
        viewModelScope.launch {
            runCatching {
                val incoming = repo.decodeRules(raw)
                if (incoming.isEmpty()) {
                    _state.update { it.copy(message = "文件中没有可导入的规则") }
                    return@runCatching
                }
                presentImportPreview(title = sourceLabel, incoming = incoming)
            }.onFailure { e ->
                _state.update { it.copy(message = "读取规则失败：${e.message}") }
            }
        }
    }

    private suspend fun presentImportPreview(title: String, incoming: List<Rule>) {
        val existing = repo.current()
        val snapshot = RuleImportPlanner.snapshot(
            existingIds = existing.map { it.id }.toSet(),
            existingCount = existing.size,
            incoming = incoming,
        )
        _state.update {
            it.copy(
                importPreview = RuleImportPreview(
                    title = title,
                    addCount = snapshot.addCount,
                    skipCount = snapshot.skipCount,
                    keepCount = snapshot.keepCount,
                    pendingRules = snapshot.pending,
                    incomingRules = snapshot.incoming,
                ),
            )
        }
    }

    fun dismissImportPreview() {
        _state.update { it.copy(importPreview = null) }
    }

    /** Default: merge — keep current rules, append only new ids. */
    fun confirmMergeImport() {
        val preview = _state.value.importPreview ?: return
        viewModelScope.launch {
            runCatching {
                val added = repo.importMerge(preview.incomingRules)
                _state.update {
                    it.copy(
                        importPreview = null,
                        message = "已合并导入「${preview.title}」：新增 $added，跳过 ${preview.incomingRules.size - added}",
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(message = "合并导入失败：${e.message}") }
            }
        }
    }

    /** Dangerous: wipe local rules and keep only the incoming list. UI must confirm twice. */
    fun confirmReplaceImport() {
        val preview = _state.value.importPreview ?: return
        viewModelScope.launch {
            runCatching {
                repo.importReplace(preview.incomingRules)
                exitSelection()
                _state.update {
                    it.copy(
                        importPreview = null,
                        message = "已替换为「${preview.title}」（${preview.incomingRules.size} 条）",
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

    /** Encode selected subset, or all rules when [onlySelected] is false. */
    fun exportJson(onlySelected: Boolean = false): String {
        val state = _state.value
        val payload = if (onlySelected && state.selectionMode) {
            state.rules.filter { it.id in state.selectedRuleIds }
        } else {
            state.rules
        }
        return RuleJson.encode(
            payload,
            exportedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
        )
    }

    /**
     * Write export JSON to cache and emit a share sheet Intent.
     * [onlySelected] uses multi-select ids; otherwise exports every rule.
     */
    fun exportAndShare(onlySelected: Boolean) {
        viewModelScope.launch {
            runCatching {
                val state = _state.value
                val payload = if (onlySelected && state.selectionMode) {
                    state.rules.filter { it.id in state.selectedRuleIds }
                } else {
                    state.rules
                }
                if (payload.isEmpty()) {
                    _state.update { it.copy(message = "没有可导出的规则") }
                    return@runCatching
                }
                val json = RuleJson.encode(
                    payload,
                    exportedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                )
                val file = withContext(Dispatchers.IO) {
                    val dir = File(getApplication<Application>().cacheDir, "rule_exports").apply { mkdirs() }
                    val stamp = DateTimeFormatter.ofPattern("yyMMdd-HHmm")
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.now())
                    val target = File(dir, "quieta-rules-$stamp-${payload.size}.json")
                    target.writeText(json)
                    target
                }
                val app = getApplication<Application>()
                val uri = FileProvider.getUriForFile(
                    app,
                    app.packageName + ".fileprovider",
                    file,
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Quieta rules x${payload.size}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                _events.emit(ConfigUiEvent.ShareRules(Intent.createChooser(intent, "导出规则")))
                if (onlySelected && state.selectionMode) {
                    _state.update {
                        it.copy(message = "已导出 ${payload.size} 条规则", selectionMode = false, selectedRuleIds = emptySet())
                    }
                } else {
                    _state.update { it.copy(message = "已导出 ${payload.size} 条规则") }
                }
            }.onFailure { e ->
                _events.emit(ConfigUiEvent.ShowError(e.message ?: "导出失败"))
            }
        }
    }

    /** Kept for tests / non-UI callers that only need the JSON string. */
    fun exportSelectedIds(): Set<String> = _state.value.selectedRuleIds

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}
