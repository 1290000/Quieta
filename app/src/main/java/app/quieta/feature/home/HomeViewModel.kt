package app.quieta.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.quieta.core.engine.RulesEngine
import app.quieta.core.model.AppChannels
import app.quieta.core.model.Channel
import app.quieta.core.model.PrivilegeId
import app.quieta.core.model.PrivilegeStatus
import app.quieta.core.model.Rule
import app.quieta.core.model.RuleAction
import app.quieta.core.privilege.FakePrivilegeBackend
import app.quieta.core.privilege.PrivilegeBackend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val privilege: PrivilegeStatus = PrivilegeStatus(PrivilegeId.NONE, available = false, label = "检测中"),
    val apps: List<AppChannels> = emptyList(),
    val plan: Map<Channel, RuleAction> = emptyMap(),
    val error: String? = null,
)

class HomeViewModel(
    private val backend: PrivilegeBackend = FakePrivilegeBackend(),
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val available = backend.isAvailable()
                val apps = if (available) FakePrivilegeBackend.demoApps() else emptyList()
                val engine = RulesEngine(demoRules())
                val channels = apps.flatMap { it.channels }
                _state.update {
                    it.copy(
                        loading = false,
                        privilege = PrivilegeStatus(
                            id = backend.id,
                            available = available,
                            label = if (available) "无特权（演示）" else "不可用",
                        ),
                        apps = apps,
                        plan = engine.plan(channels),
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(loading = false, error = e.message ?: "加载失败")
                }
            }
        }
    }

    private fun demoRules(): List<Rule> = listOf(
        Rule(id = "mute-marketing", nameContains = "推广", action = RuleAction.MUTE),
        Rule(id = "mute-promo", nameContains = "促销", action = RuleAction.MUTE),
        Rule(id = "mute-marketing-en", nameContains = "marketing", action = RuleAction.MUTE),
        Rule(id = "mute-live", nameContains = "直播", action = RuleAction.DOWNGRADE),
    )
}
