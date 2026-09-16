package app.quieta.feature.home

import android.app.Application
import android.os.SystemClock
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
import app.quieta.core.privilege.selectAuthorizer
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    val checkingPrivilege: Boolean = true,
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
    val rootDescription: String = "",
    val rootWriteSupported: Boolean = false,
    val shizukuAvailable: Boolean = false,
    val shizukuAuthorized: Boolean = false,
    val dhizukuAvailable: Boolean = false,
    val searchQuery: String = "",
    val filters: ChannelListFilters = ChannelListFilters(),
    val sort: ChannelSort = ChannelSort.CHANNEL_COUNT,
    val expandedPackages: Set<String> = emptySet(),
    val listItems: List<ChannelListAppItem> = emptyList(),
    val listSummary: String = "",
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val shizukuBackend = ShizukuBackend(application)
    private val dhizukuBackend = app.quieta.core.privilege.dhizuku.DhizukuBackend(application)
    private val rootBackend = app.quieta.core.privilege.root.RootBackend(
        context = application,
    )
    /** Active backend for list/set, with Root-first automatic selection. */
    private var backend: app.quieta.core.privilege.PrivilegeBackend = shizukuBackend
    private val ruleRepository = RuleRepository.getInstance(application)
    private val inventoryStore = ChannelInventoryStore.getInstance(application)
    private val appSettings = AppSettings(application)
    private val muteLog = app.quieta.core.engine.MuteLogStore.getInstance(application)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var inventoryJob: Job? = null
    private var refreshJob: Job? = null
    private val cacheJob: Job

    init {
        viewModelScope.launch {
            appSettings.autoMuteNewChannels.collect { autoMute ->
                _state.update { it.copy(autoMuteOn = autoMute) }
            }
        }
        viewModelScope.launch {
            appSettings.lastKnownPrivilege.collect { cached ->
                if (cached != null && _state.value.privilege.id == PrivilegeId.NONE) {
                    _state.update {
                        it.copy(
                            gate = PrivilegeGate.READY,
                            privilege = cached,
                        )
                    }
                }
            }
        }
        cacheJob = viewModelScope.launch {
            val cached = inventoryStore.current()
            if (cached.isNotEmpty()) {
                _state.update { it.copy(apps = cached) }
            }
        }
        viewModelScope.launch {
            ruleRepository.current()
            combine(
                ruleRepository.rules,
                state.map { Triple(it.apps, it.searchQuery, it.filters) }.distinctUntilChanged(),
                state.map { Pair(it.sort, it.expandedPackages) }.distinctUntilChanged(),
            ) { rules, searchTriple, sortPair ->
                ListProjectionInput(
                    rules = rules,
                    apps = searchTriple.first,
                    query = searchTriple.second,
                    filters = searchTriple.third,
                    sort = sortPair.first,
                    expanded = sortPair.second,
                )
            }.collectLatest { input ->
                val plan = withContext(Dispatchers.Default) {
                    RulesEngine(input.rules).plan(input.apps.flatMap { it.channels })
                }
                val items = withContext(Dispatchers.Default) {
                    ChannelListProjector.project(
                        apps = input.apps,
                        query = input.query,
                        filters = input.filters,
                        sort = input.sort,
                        plan = plan,
                        expandedPackages = input.expanded,
                    )
                }
                val visibleChannels = items.sumOf { it.channels.size }
                val summary = if (input.query.isNotBlank() || input.filters.isActive) {
                    "匹配 ${items.size} 个应用 · $visibleChannels 个渠道"
                } else {
                    "${items.size} 个应用 · $visibleChannels 个渠道"
                }
                _state.update {
                    if (it.apps != input.apps || ruleRepository.rules.value != input.rules) {
                        it
                    } else {
                        it.copy(
                            rulesCount = input.rules.size,
                            plan = plan,
                            listItems = items,
                            listSummary = summary,
                        )
                    }
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

    private data class ListProjectionInput(
        val rules: List<app.quieta.core.model.Rule>,
        val apps: List<AppChannels>,
        val query: String,
        val filters: ChannelListFilters,
        val sort: ChannelSort,
        val expanded: Set<String>,
    )

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun setSort(sort: ChannelSort) {
        _state.update { it.copy(sort = sort) }
    }

    fun toggleFilter(transform: (ChannelListFilters) -> ChannelListFilters) {
        _state.update { it.copy(filters = transform(it.filters)) }
    }

    fun toggleExpanded(packageName: String) {
        _state.update { state ->
            val next = if (packageName in state.expandedPackages) {
                state.expandedPackages - packageName
            } else {
                state.expandedPackages + packageName
            }
            state.copy(expandedPackages = next)
        }
    }

    fun expandAllVisible() {
        val pkgs = _state.value.listItems.map { it.app.packageName }.toSet()
        if (pkgs.isEmpty()) return
        _state.update { it.copy(expandedPackages = it.expandedPackages + pkgs) }
    }

    fun collapseAll() {
        _state.update { it.copy(expandedPackages = emptySet()) }
    }

    fun setPreferredAuthorizer(authorizer: app.quieta.core.settings.PreferredAuthorizer) {
        viewModelScope.launch {
            appSettings.setPreferredAuthorizer(authorizer)
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        inventoryJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(checkingPrivilege = true, loading = true, error = null) }
            val shizukuProbe = async(Dispatchers.IO) { CapabilityProbe.probeShizuku() }
            val dhizukuProbe = async(Dispatchers.IO) { dhizukuBackend.isAvailable() }
            val rootProbe = async(Dispatchers.IO) { rootBackend.probeCapabilities() }
            val shizuku = shizukuProbe.await()
            val dhizukuOk = dhizukuProbe.await()
            val rootCapabilities = rootProbe.await()
            val rootIdentity = rootCapabilities.identity
            val rootOk = rootCapabilities.readable
            val rootLabel = rootIdentity.manager ?: appContext.getString(app.quieta.R.string.privilege_root_unknown)
            val managers = withContext(Dispatchers.IO) { rootBackend.installedManagers(rootIdentity) }
            val rootDescription = buildString {
                append(rootLabel)
                if (managers.isNotEmpty()) append(appContext.getString(app.quieta.R.string.privilege_root_managers, managers.joinToString()))
                append("\n")
                append(appContext.getString(when {
                    !rootIdentity.available -> app.quieta.R.string.privilege_root_denied
                    !rootOk -> app.quieta.R.string.privilege_root_read_failed
                    !rootCapabilities.writeSupported -> app.quieta.R.string.privilege_root_read_only
                    else -> app.quieta.R.string.privilege_root_write_pending
                }))
            }
            val pref = _state.value.preferredAuthorizer

            val capabilityUpdate: (HomeUiState) -> HomeUiState = {
                it.copy(
                    checkingPrivilege = false,
                    shizukuAvailable = shizuku.binderAlive,
                    shizukuAuthorized = shizuku.binderAlive && shizuku.permissionGranted,
                    dhizukuAvailable = dhizukuOk,
                    rootAvailable = rootOk,
                    rootLabel = rootLabel,
                    rootDescription = rootDescription,
                    rootWriteSupported = rootCapabilities.writeSupported,
                )
            }

            val shizukuOk = shizuku.binderAlive && shizuku.permissionGranted
            val chosen = when (selectAuthorizer(pref, rootOk, shizukuOk, dhizukuOk)) {
                PrivilegeId.ROOT -> rootBackend
                PrivilegeId.SHIZUKU -> shizukuBackend
                PrivilegeId.DHIZUKU -> dhizukuBackend
                PrivilegeId.NONE -> null
            }

            if (chosen != null) {
                backend = chosen
                val label = when (chosen.id) {
                    PrivilegeId.SHIZUKU -> "Shizuku"
                    PrivilegeId.DHIZUKU -> "Dhizuku"
                    PrivilegeId.ROOT -> "ROOT ($rootLabel)"
                    PrivilegeId.NONE -> "无特权"
                }
                _state.update {
                    capabilityUpdate(it).copy(
                        gate = PrivilegeGate.READY,
                        error = null,
                        privilege = PrivilegeStatus(
                            id = chosen.id,
                            available = true,
                            label = label,
                        ),
                    )
                }
                appSettings.setLastKnownPrivilege(
                    PrivilegeStatus(chosen.id, available = true, label = label),
                )
                startInventoryRefresh()
            } else if (pref == app.quieta.core.settings.PreferredAuthorizer.NONE) {
                _state.update {
                    capabilityUpdate(it).copy(
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
            } else if (pref in listOf(app.quieta.core.settings.PreferredAuthorizer.AUTO,
                    app.quieta.core.settings.PreferredAuthorizer.SHIZUKU) &&
                shizuku.binderAlive && !shizuku.permissionGranted) {
                _state.update {
                    capabilityUpdate(it).copy(
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
                    capabilityUpdate(it).copy(
                        loading = false,
                        gate = PrivilegeGate.SHIZUKU_UNAVAILABLE,
                        privilege = PrivilegeStatus(
                            id = PrivilegeId.NONE,
                            available = false,
                            label = "未检测到可用特权",
                        ),
                        progress = null,
                    )
                }
            }
        }
    }

    /** Incremental package add/remove/replace (LibChecker LocalPackageChangeObserver pattern). */
    fun onPackageChanged(packageName: String, removed: Boolean) {
        if (packageName == appContext.packageName) return
        viewModelScope.launch {
            if (_state.value.checkingPrivilege || _state.value.gate != PrivilegeGate.READY) return@launch
            val activeBackend = backend
            if (removed) {
                inventoryStore.remove(packageName)
                val next = _state.value.apps.filterNot { it.packageName == packageName }
                publishApps(next, loading = _state.value.loading)
            } else {
                val channels = try {
                    withContext(Dispatchers.IO) { activeBackend.listChannels(packageName) }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    return@launch
                }
                if (backend !== activeBackend || !_state.value.privilege.available) return@launch
                if (channels.isEmpty()) return@launch
                val label = withContext(Dispatchers.IO) {
                    runCatching {
                        val ai = appContext.packageManager.getApplicationInfo(packageName, 0)
                        appContext.packageManager.getApplicationLabel(ai).toString()
                    }.getOrDefault(packageName)
                }
                val item = AppChannels(packageName, label, channels)
                inventoryStore.upsert(item)
                val next = _state.value.apps.filterNot { it.packageName == packageName } + item
                publishApps(sortApps(next), loading = _state.value.loading)
            }
        }
    }

    fun applyBatchMute() {
        viewModelScope.launch {
            if (_state.value.checkingPrivilege || _state.value.gate != PrivilegeGate.READY) return@launch
            val activeBackend = backend
            val rules = ruleRepository.current()
            val apps = _state.value.apps
            val plan = withContext(Dispatchers.Default) { RulesEngine(rules).plan(apps.flatMap { it.channels }) }
            _state.update { it.copy(plan = plan) }
            if (plan.isEmpty()) {
                _state.update { it.copy(muteResult = MuteResult(0, 0, 0, listOf("没有可执行的规则"))) }
                return@launch
            }
            _state.update { it.copy(progress = "正在批量静音…", muteResult = null) }
            val result = withContext(Dispatchers.Default) {
                BatchMuteUseCase(activeBackend).apply(plan)
            }
            if (activeBackend.id == PrivilegeId.ROOT && backend === activeBackend) {
                _state.update { it.copy(rootDescription = it.rootDescription.substringBeforeLast('\n') + "\n" +
                    appContext.getString(if (result.failed == 0 && result.success > 0)
                        app.quieta.R.string.privilege_root_write_verified else app.quieta.R.string.privilege_root_write_failed)) }
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
            // Re-read inventory so the home list shows the post-mute importance.
            startInventoryRefresh()
        }
    }

    /**
     * Show disk cache first, then scan with limited concurrency and publish in batches
     * so the UI stays scrollable (LibChecker InitializeAppListUseCase pattern).
     */
    private fun startInventoryRefresh() {
        inventoryJob?.cancel()
        val activeBackend = backend
        inventoryJob = viewModelScope.launch {
            cacheJob.join()
            val cached = withContext(Dispatchers.IO) { inventoryStore.current() }
            if (cached.isNotEmpty()) {
                publishApps(cached, loading = true, progress = "刷新通知渠道…")
            } else {
                _state.update { it.copy(loading = true, progress = "读取应用列表…") }
            }

            runCatching {
                ruleRepository.current()
                val baseApps = withContext(Dispatchers.IO) { InstalledApps.load(appContext) }
                val results = HashMap<String, AppChannels>(baseApps.size + cached.size)
                cached.forEach { results[it.packageName] = it }

                val mutex = Mutex()
                // Cap Binder concurrency — hundreds of parallel Shizuku calls jank the process.
                val semaphore = Semaphore(SCAN_CONCURRENCY)
                var done = 0
                var failures = 0
                var lastPublish = 0L
                val total = baseApps.size

                withContext(Dispatchers.Default) {
                    baseApps.map { app ->
                        async {
                            semaphore.withPermit {
                                val channels = try {
                                    activeBackend.listChannels(app.packageName)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    null
                                }
                                mutex.withLock {
                                    if (channels == null) {
                                        failures++
                                    } else if (channels.isEmpty()) {
                                        results.remove(app.packageName)
                                    } else {
                                        results[app.packageName] = app.copy(channels = channels)
                                    }
                                    done++
                                    val now = SystemClock.elapsedRealtime()
                                    if (done == total || (done % PUBLISH_BATCH == 0 && now - lastPublish >= PUBLISH_INTERVAL_MS)) {
                                        lastPublish = now
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

                val finalApps = withContext(Dispatchers.Default) { sortApps(results.values.toList()) }
                if (failures > 0) {
                    _state.update { it.copy(error = appContext.getString(app.quieta.R.string.inventory_partial_failure, failures)) }
                }
                // Do not clobber a good cache with an empty failed scan.
                if (finalApps.isNotEmpty()) {
                    withContext(Dispatchers.IO) { inventoryStore.replaceAll(finalApps) }
                    app.quieta.core.auto.AutoMuteCoordinator.seedKnown(finalApps.flatMap { it.channels })
                }
                publishApps(
                    finalApps,
                    loading = false,
                    progress = null,
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
        autoMuteOn: Boolean? = null,
    ) {
        _state.update {
            it.copy(
                loading = loading,
                progress = progress,
                apps = apps,
                autoMuteOn = autoMuteOn ?: it.autoMuteOn,
            )
        }
    }

    private companion object {
        const val SCAN_CONCURRENCY = 6
        const val PUBLISH_BATCH = 20
        const val PUBLISH_INTERVAL_MS = 200L
    }
}
