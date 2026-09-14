package app.quieta.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quieta.core.engine.RulesEngine
import app.quieta.core.model.AppChannels
import app.quieta.core.model.Channel
import app.quieta.core.model.PrivilegeId
import app.quieta.core.model.PrivilegeStatus
import app.quieta.core.model.Rule
import app.quieta.core.model.RuleAction
import app.quieta.core.privilege.CapabilityProbe
import app.quieta.core.privilege.InstalledApps
import app.quieta.core.privilege.PrivilegeBackends
import app.quieta.core.privilege.shizuku.ShizukuBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val progress: String? = null,
    val error: String? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val backend = ShizukuBackend()
    private val appContext = application.applicationContext

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update {
                it.copy(loading = true, error = null, progress = "检测 Shizuku…", gate = PrivilegeGate.CHECKING)
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

    private suspend fun loadInventory() = withContext(Dispatchers.Default) {
        _state.update {
            it.copy(loading = true, gate = PrivilegeGate.READY, progress = "读取应用列表…")
        }
        runCatching {
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
            val engine = RulesEngine(demoRules())
            val channels = withChannels.flatMap { it.channels }
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

    private fun demoRules(): List<Rule> = listOf(
        Rule(id = "mute-marketing", nameContains = "推广", action = RuleAction.MUTE),
        Rule(id = "mute-promo", nameContains = "促销", action = RuleAction.MUTE),
        Rule(id = "mute-marketing-en", nameContains = "marketing", action = RuleAction.MUTE),
        Rule(id = "mute-ad", nameContains = "广告", action = RuleAction.MUTE),
        Rule(id = "mute-live", nameContains = "直播", action = RuleAction.DOWNGRADE),
    )
}
