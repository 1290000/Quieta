package app.quieta.feature.backup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quieta.core.engine.ApplyChannelSnapshotUseCase
import app.quieta.core.engine.ChannelSnapshotImportPlanner
import app.quieta.core.engine.SnapshotApplyReport
import app.quieta.core.engine.SnapshotImportPlan
import app.quieta.core.privilege.PrivilegeBackend
import app.quieta.core.repo.ChannelInventoryStore
import app.quieta.core.repo.ChannelSnapshotJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChannelImportUiState(
    val message: String? = null,
    val busy: Boolean = false,
    val sourceLabel: String? = null,
    val snapshot: ChannelSnapshotJson.Snapshot? = null,
    val plan: SnapshotImportPlan? = null,
    val includeSystem: Boolean = false,
    val applying: Boolean = false,
    val progressText: String? = null,
    val report: SnapshotApplyReport? = null,
)

/**
 * Channel-snapshot import: parse file → dry-run plan → apply importance → readback report.
 * Privilege backend is supplied by HomeViewModel at apply time (same probe status).
 */
class ChannelImportViewModel(application: Application) : AndroidViewModel(application) {

    private val inventory = ChannelInventoryStore.getInstance(application)

    private val _state = MutableStateFlow(ChannelImportUiState())
    val state: StateFlow<ChannelImportUiState> = _state.asStateFlow()

    private var applyJob: Job? = null

    fun previewFromUri(uri: Uri) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    busy = true,
                    message = "读取快照…",
                    report = null,
                    progressText = null,
                )
            }
            runCatching {
                val raw = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { stream -> stream.readBytes().decodeToString() }
                        ?: error("无法读取文件")
                }
                val snapshot = withContext(Dispatchers.Default) { ChannelSnapshotJson.decode(raw) }
                if (snapshot.apps.isEmpty()) {
                    _state.update {
                        it.copy(
                            busy = false,
                            snapshot = null,
                            plan = null,
                            sourceLabel = null,
                            message = "快照为空，没有可导入的应用",
                        )
                    }
                    return@runCatching
                }
                val label = uri.lastPathSegment?.substringAfterLast('/') ?: "渠道快照"
                val local = inventory.current()
                val plan = ChannelSnapshotImportPlanner.plan(
                    snapshot = snapshot,
                    local = local,
                    includeSystem = _state.value.includeSystem,
                )
                _state.update {
                    it.copy(
                        busy = false,
                        snapshot = snapshot,
                        plan = plan,
                        sourceLabel = label,
                        message = planSummary(plan, local.isEmpty()),
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        busy = false,
                        message = "读取失败：${e.message}",
                    )
                }
            }
        }
    }

    fun setIncludeSystem(enabled: Boolean) {
        _state.update { it.copy(includeSystem = enabled) }
        val snapshot = _state.value.snapshot ?: return
        viewModelScope.launch {
            val local = inventory.current()
            val plan = ChannelSnapshotImportPlanner.plan(snapshot, local, enabled)
            _state.update { it.copy(plan = plan, message = planSummary(plan, local.isEmpty())) }
        }
    }

    fun applyWithBackend(backend: PrivilegeBackend?) {
        val plan = _state.value.plan
        if (plan == null) {
            _state.update { it.copy(message = "请先选择并预览快照文件") }
            return
        }
        if (backend == null) {
            _state.update { it.copy(message = "提权未就绪，请先在主页完成授权") }
            return
        }
        if (plan.applyCount == 0) {
            _state.update { it.copy(message = "没有需要写入的渠道（可能全部相同或已跳过）") }
            return
        }
        if (_state.value.applying) return
        applyJob?.cancel()
        applyJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    applying = true,
                    report = null,
                    message = null,
                    progressText = "正在写入 0/${plan.applyCount}…",
                )
            }
            val report = withContext(Dispatchers.IO) {
                ApplyChannelSnapshotUseCase(backend).apply(plan.apply) { done, total ->
                    _state.update { s -> s.copy(progressText = "正在写入 $done/$total…") }
                }
            }
            _state.update {
                it.copy(
                    applying = false,
                    progressText = null,
                    report = report,
                    message = "写入成功 ${report.writeOk}/${report.total} · 回读确认 ${report.verified}" +
                        if (report.extrasOnly > 0) " · 仅部分字段 ${report.extrasOnly}" else "",
                )
            }
            // Refresh inventory cache so home list reflects new importance.
            runCatching { inventory.replaceAll(inventory.current()) }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun planSummary(plan: SnapshotImportPlan, emptyLocal: Boolean): String {
        if (emptyLocal && plan.applyCount == 0) {
            return "本机暂无盘点缓存，无法对照。请先在主页刷新渠道后再预览。"
        }
        return buildString {
            append("将写入 ${plan.applyCount}")
            append(" · 相同 ${plan.sameCount}")
            append(" · 跳过 ${plan.skips.size}")
            if (plan.appMissingCount > 0) append("（未装 ${plan.appMissingCount} App）")
            if (plan.channelMissingCount > 0) append("（无渠道 ${plan.channelMissingCount}）")
            if (plan.systemHeldCount > 0) append("（系统 ${plan.systemHeldCount}）")
        }
    }
}
