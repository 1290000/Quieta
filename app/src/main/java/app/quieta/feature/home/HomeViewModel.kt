package app.quieta.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quieta.core.engine.BatchMuteUseCase
import app.quieta.core.engine.MuteResult
import app.quieta.core.engine.RulesEngine
import app.quieta.core.model.AppChannels
import app.quieta.core.model.Channel
import app.quieta.core.model.PrivilegeId
import app.quieta.core.model.PrivilegeStatus
import app.quieta.core.model.RuleAction
import app.quieta.core.privilege.CapabilityProbe
import app.quieta.core.privilege.InstalledApps
import app.quieta.core.privilege.shizuku.ShizukuBackend
import app.quieta.core.repo.RuleRepository
import app.quieta.core.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

enum class PrivilegeGate {
    CHECKING,
    NEED_PERMISSION,
    SHIZUKU_UNAVAILABLE,
    READY,
}

data class HomeUiState(
    val loading: Boolean = true,
    val gate: PrivilegeGate = PrivilegeGate.CHECKING,
    val privilege: PrivilegeStatus = PrivilegeStatus(
        id = PrivilegeId.NONE,
        available = false,
        label = "检测中",
    ),
    val apps: List<AppChannels> = emptyList(),
    val plan: Map<Channel, RuleAction> = emptyMap(),
    val rulesCount: Int = 0,
    val autoMuteOn: Boolean = false,
    val progress: String? = null,
    val muteResult: MuteResult? = null,
    val error: String? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val backend = ShizukuBackend()
    private val appContext = application.applicationContext
    private val ruleRepository = RuleRepository(application)
    private val batchMute = BatchMuteUseCase(backend)
    private val appSettings = AppSettings(application)
    private val muteLog = app.quieta.core.engine.MuteLogStore(application)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    error = null,
                    muteResult = null,
                    progress = "检测 Shizuku…",
                    gate = PrivilegeGate.CHECKING,
                )
            }
            val caps = CapabilityProbe.probeShizuku()
            when {
                !caps.binderAlive -> {
                    _state.update {
                        it.copy(
                            loading = false,
                            gate = PrivilegeGate.SHIZUKU_UNAVAILABLE,
                            privilege = PrivilegeStatus(
                                id = PrivilegeId.SHIZUKU,
                                available = false,
                                label = "Shizuku 未运行",
                            ),
                            progress = null,
                            apps = emptyList(),
                            plan = emptyMap(),
                        )
                    }
                }
                !caps.permissionGranted -> {
                    _state.update {
                        it.copy(
                            loading = false,
                            gate = PrivilegeGate.NEED_PERMISSION,
                            privilege = PrivilegeStatus(
                                id = PrivilegeId.SHIZUKU,
                                available = false,
                                label = "需要 Shizuku 授权",
                            ),
                            progress = null,
                        )
                    }
                }
                else -> loadInventory()
            }
        }
    }

    fun applyBatchMute() {
        viewModelScope.launch {
            val plan = _state.value.plan
            if (plan.isEmpty()) {
                _state.update { it.copy(muteResult = MuteResult(0, 0, 0, listOf("没有可执行的规则"))) }
                return@launch
            }
            _state.update { it.copy(progress = "正在批量静音…", muteResult = null) }
            val result = withContext(Dispatchers.Default) { batchMute.apply(plan) }
            muteLog.append(
                app.quieta.core.engine.MuteLogEntry(
                    label = "按规则静音",
                    detail = "成功 ${result.success} / ${result.total}，失败 ${result.failed}",
                    time = app.quieta.core.engine.MuteLogStore.now(),
                    tag = if (result.failed == 0) "成功" else "部分失败",
                ),
            )
            _state.update {
                it.copy(
                    progress = null,
                    muteResult = result,
                )
            }
        }
    }

    private suspend fun loadInventory() = withContext(Dispatchers.Default) {
        _state.update {
            it.copy(loading = true, gate = PrivilegeGate.READY, progress = "读取应用列表…")
        }
        runCatching {
            val rules = ruleRepository.rules.first()
            val baseApps = InstalledApps.load(appContext)
            _state.update { s -> s.copy(progress = "读取通知渠道 ${baseApps.size} 个应用…") }
            val withChannels = supervisorScope {
                baseApps.map { app ->
                    async {
                        val channels = backend.listChannels(app.packageName)
                        if (channels.isEmpty()) null else app.copy(channels = channels)
                    }
                }.awaitAll().filterNotNull()
            }
            val engine = RulesEngine(rules)
            val channels = withChannels.flatMap { it.channels }
            app.quieta.core.auto.AutoMuteCoordinator.seedKnown(channels)
            _state.update {
                it.copy(
                    loading = false,
                    progress = null,
                    privilege = PrivilegeStatus(
                        id = backend.id,
                        available = true,
                        label = "Shizuku",
                    ),
                    apps = withChannels,
                    plan = engine.plan(channels),
                    rulesCount = rules.size,
                    autoMuteOn = appSettings.autoMuteNewChannels.first(),
                )
            }
        }.onFailure { e ->
            _state.update {
                it.copy(
                    loading = false,
                    progress = null,
                    error = e.message ?: "盘点失败",
                )
            }
        }
    }
}
