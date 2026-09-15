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
import app.quieta.core.repo.ChannelInventoryStore
import app.quieta.core.repo.RuleRepository
import app.quieta.core.settings.AppSettings
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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
    val preferredAuthorizer: app.quieta.core.settings.PreferredAuthorizer =
        app.quieta.core.settings.PreferredAuthorizer.AUTO,
    val rootAvailable: Boolean = false,
    val rootLabel: String = "无",
    val shizukuAvailable: Boolean = false,
    val shizukuAuthorized: Boolean = false,
    val dhizukuAvailable: Boolean = false,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val shizukuBackend = ShizukuBackend(application)
    private val dhizukuBackend = app.quieta.core.privilege.dhizuku.DhizukuBackend(application)
    private val rootBackend = app.quieta.core.privilege.root.RootBackend(
        context = application,
        fallback = shizukuBackend,
    )
    /** Active backend for list/set — chosen in refresh() (Shizuku → Dhizuku → Root). */
    private var backend: app.quieta.core.privilege.PrivilegeBackend = shizukuBackend
    private val ruleRepository = RuleRepository.getInstance(application)
    private val inventoryStore = ChannelInventoryStore.getInstance(application)
    private val batchMute = BatchMuteUseCase(shizukuBackend)
    private val appSettings = AppSettings(application)
    private val muteLog = app.quieta.core.engine.MuteLogStore.getInstance(application)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var inventoryJob: Job? = null

    init {
        viewModelScope.launch {
            val autoMute = runCatching { appSettings.autoMuteNewChannels.first() }.getOrDefault(false)
            _state.update { it.copy(autoMuteOn = autoMute) }
            ruleRepository.rules.collect { rules ->
                val channels = _state.value.apps.flatMap { it.channels }
                _state.update {
                    it.copy(
                        rulesCount = rules.size,
                        plan = RulesEngine(rules).plan(channels),
                    )
                }
            }
        }
        // Real-time status when user changes authorizer on Privilege page.
        viewModelScope.launch {
            appSettings.preferredAuthorizer.collect { pref ->
                _state.update { it.copy(preferredAuthorizer = pref) }
                refresh()
            }
        }
    }

    fun setPreferredAuthorizer(authorizer: app.quieta.core.settings.PreferredAuthorizer) {
        viewModelScope.launch {
            appSettings.setPreferredAuthorizer(authorizer)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val shizuku = CapabilityProbe.probeShizuku()
            val dhizukuOk = runCatching { dhizukuBackend.isAvailable() }.getOrDefault(false)
            val rootOk = runCatching { rootBackend.isAvailable() }.getOrDefault(false)
            val rootLabel = rootBackend.rootImplementationLabel()
            val pref = _state.value.preferredAuthorizer

            _state.update {
                it.copy(
                    shizukuAvailable = shizuku.binderAlive,
                    shizukuAuthorized = shizuku.binderAlive && shizuku.permissionGranted,
                    dhizukuAvailable = dhizukuOk,
                    rootAvailable = rootOk,
                    rootLabel = rootLabel,
                )
            }

            val shizukuOk = shizuku.binderAlive && shizuku.permissionGranted
            val chosen = when (pref) {
                app.quieta.core.settings.PreferredAuthorizer.SHIZUKU ->
                    if (shizukuOk) shizukuBackend else null
                app.quieta.core.settings.PreferredAuthorizer.DHIZUKU ->
                    if (dhizukuOk) dhizukuBackend else null
                app.quieta.core.settings.PreferredAuthorizer.ROOT ->
                    if (rootOk) rootBackend else null
                app.quieta.core.settings.PreferredAuthorizer.NONE -> null
                app.quieta.core.settings.PreferredAuthorizer.AUTO -> when {
                    shizukuOk -> shizukuBackend
                    dhizukuOk -> dhizukuBackend
                    rootOk -> rootBackend
                    else -> null
                }
            }

            if (chosen != null) {
                backend = chosen
                val label = when (chosen.id) {
                    PrivilegeId.SHIZUKU -> "Shizuku"
                    PrivilegeId.DHIZUKU -> "Dhizuku"
                    PrivilegeId.ROOT -> "ROOT · $rootLabel"
                    PrivilegeId.NONE -> "无特权"
                }
                _state.update {
                    it.copy(
                        gate = PrivilegeGate.READY,
                        privilege = PrivilegeStatus(
                            id = chosen.id,
                            available = true,
                            label = label,
                        ),
                    )
                }
                startInventoryRefresh()
            } else if (pref == app.quieta.core.settings.PreferredAuthorizer.NONE) {
                _state.update {
                    it.copy(
                        loading = false,
                        gate = PrivilegeGate.SHIZUKU_UNAVAILABLE,
                        privilege = PrivilegeStatus(
                            id = PrivilegeId.NONE,
                            available = false,
                            label = "无特权",
                        ),
                        progress = null,
                    )
                }
            } else if (shizuku.binderAlive && !shizuku.permissionGranted) {
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
            } else {
                _state.update {
                    it.copy(
                        loading = false,
                        gate = PrivilegeGate.SHIZUKU_UNAVAILABLE,
                        privilege = PrivilegeStatus(
                            id = PrivilegeId.SHIZUKU,
                            available = false,
                            label = "未检测到可用特权",
                        ),
                        progress = null,
                        apps = emptyList(),
                        plan = emptyMap(),
                    )
                }
            }
        }
    }

    /** Incremental package add/remove/replace (LibChecker LocalPackageChangeObserver pattern). */
    fun onPackageChanged(packageName: String, removed: Boolean) {
        if (packageName == appContext.packageName) return
        viewModelScope.launch {
            if (removed) {
                inventoryStore.remove(packageName)
                val next = _state.value.apps.filterNot { it.packageName == packageName }
                publishApps(next, loading = _state.value.loading)
            } else {
                val channels = runCatching { backend.listChannels(packageName) }.getOrDefault(emptyList())
                if (channels.isEmpty()) return@launch
                val label = runCatching {
                    val ai = appContext.packageManager.getApplicationInfo(packageName, 0)
                    appContext.packageManager.getApplicationLabel(ai).toString()
                }.getOrDefault(packageName)
                val item = AppChannels(packageName, label, channels)
                inventoryStore.upsert(item)
                val next = _state.value.apps.filterNot { it.packageName == packageName } + item
                publishApps(sortApps(next), loading = _state.value.loading)
            }
        }
    }

    fun applyBatchMute() {
        viewModelScope.launch {
            val rules = runCatching { ruleRepository.rules.first() }.getOrDefault(emptyList())
            val channels = _state.value.apps.flatMap { it.channels }
            val plan = RulesEngine(rules).plan(channels)
            _state.update { it.copy(plan = plan) }
            if (plan.isEmpty()) {
                _state.update { it.copy(muteResult = MuteResult(0, 0, 0, listOf("没有可执行的规则"))) }
                return@launch
            }
            _state.update { it.copy(progress = "正在批量静音…", muteResult = null) }
            val result = withContext(Dispatchers.Default) {
                BatchMuteUseCase(backend).apply(plan)
            }
            muteLog.append(
                app.quieta.core.engine.MuteLogEntry(
                    label = "按规则静音",
                    detail = "成功 ${result.success} / ${result.total}，失败 ${result.failed}",
                    time = app.quieta.core.engine.MuteLogStore.now(),
                    tag = if (result.failed == 0) "成功" else "部分失败",
                ),
            )
            _state.update {
                it.copy(progress = null, muteResult = result)
            }
        }
    }

    /**
     * Show disk cache first, then scan with limited concurrency and publish in batches
     * so the UI stays scrollable (LibChecker InitializeAppListUseCase pattern).
     */
    private fun startInventoryRefresh() {
        inventoryJob?.cancel()
        inventoryJob = viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { inventoryStore.current() }
            if (cached.isNotEmpty()) {
                publishApps(sortApps(cached), loading = true, progress = "刷新通知渠道…")
            } else {
                _state.update { it.copy(loading = true, progress = "读取应用列表…") }
            }

            runCatching {
                val rules = ruleRepository.rules.first()
                val baseApps = withContext(Dispatchers.Default) { InstalledApps.load(appContext) }
                val results = HashMap<String, AppChannels>(baseApps.size + cached.size)
                cached.forEach { results[it.packageName] = it }

                val mutex = Mutex()
                // Cap Binder concurrency — hundreds of parallel Shizuku calls jank the process.
                val semaphore = Semaphore(SCAN_CONCURRENCY)
                var done = 0
                val total = baseApps.size

                withContext(Dispatchers.Default) {
                    baseApps.map { app ->
                        async {
                            semaphore.withPermit {
                                val channels = try {
                                    backend.listChannels(app.packageName)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Throwable) {
                                    emptyList()
                                }
                                mutex.withLock {
                                    if (channels.isEmpty()) {
                                        results.remove(app.packageName)
                                    } else {
                                        results[app.packageName] = app.copy(channels = channels)
                                    }
                                    done++
                                    if (done % PUBLISH_BATCH == 0 || done == total) {
                                        val snapshot = sortApps(results.values.toList())
                                        publishApps(
                                            snapshot,
                                            loading = done < total,
                                            progress = if (done < total) {
                                                "读取通知渠道 $done / $total…"
                                            } else {
                                                null
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }.awaitAll()
                }

                val finalApps = sortApps(results.values.toList())
                // Do not clobber a good cache with an empty failed scan.
                if (finalApps.isNotEmpty()) {
                    withContext(Dispatchers.IO) { inventoryStore.replaceAll(finalApps) }
                    app.quieta.core.auto.AutoMuteCoordinator.seedKnown(finalApps.flatMap { it.channels })
                }
                publishApps(
                    finalApps,
                    loading = false,
                    progress = null,
                    rules = rules,
                    autoMuteOn = appSettings.autoMuteNewChannels.first(),
                )
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _state.update {
                    it.copy(loading = false, progress = null, error = e.message ?: "盘点失败")
                }
            }
        }
    }

    private fun sortApps(apps: List<AppChannels>): List<AppChannels> =
        apps.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.packageName }))

    private suspend fun publishApps(
        apps: List<AppChannels>,
        loading: Boolean,
        progress: String? = null,
        rules: List<app.quieta.core.model.Rule>? = null,
        autoMuteOn: Boolean? = null,
    ) {
        val activeRules = rules ?: runCatching { ruleRepository.rules.first() }.getOrDefault(emptyList())
        val plan = RulesEngine(activeRules).plan(apps.flatMap { it.channels })
        _state.update {
            it.copy(
                loading = loading,
                progress = progress,
                apps = apps,
                plan = plan,
                rulesCount = activeRules.size,
                autoMuteOn = autoMuteOn ?: it.autoMuteOn,
                gate = PrivilegeGate.READY,
                privilege = PrivilegeStatus(
                    id = backend.id,
                    available = true,
                    label = "Shizuku",
                ),
            )
        }
    }

    private companion object {
        const val SCAN_CONCURRENCY = 6
        const val PUBLISH_BATCH = 20
    }
}
