package app.quieta.feature.home

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quieta.core.engine.BatchMuteUseCase
import app.quieta.core.engine.ChannelActionUseCase
import app.quieta.core.engine.MuteResult
import app.quieta.core.engine.MuteSnapshot
import app.quieta.core.engine.MuteSnapshotEntry
import app.quieta.core.engine.MuteUndoStore
import app.quieta.core.engine.RulesEngine
import app.quieta.core.model.AppChannels
import app.quieta.core.model.Channel
import app.quieta.core.model.ChannelImportance
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
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
    val baseListItems: List<ChannelListAppItem> = emptyList(),
    val listItems: List<ChannelListAppItem> = emptyList(),
    val listSummary: String = "",
    val mutePreview: MutePreview? = null,
    val muteScope: MuteScope = MuteScope.ALL,
    val canUndoLastBatch: Boolean = false,
    val undoLabel: String? = null,
)

enum class MuteScope {
    ALL,
    FILTERED,
}

data class MutePreviewItem(
    val packageName: String,
    val appLabel: String,
    val channelName: String,
    val channelId: String,
    val action: RuleAction,
    val reason: String,
)

data class MutePreview(
    val items: List<MutePreviewItem>,
    val muteCount: Int,
    val downgradeCount: Int,
    val scope: MuteScope = MuteScope.ALL,
    val filterActive: Boolean = false,
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
    private val muteUndo = MuteUndoStore.getInstance(application)

    private val _state = MutableStateFlow(seedHomeState(appSettings))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var inventoryJob: Job? = null
    private var refreshJob: Job? = null
    private var authorizerBootstrapped = false
    private var projectionEmitted = false
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
                            checkingPrivilege = false,
                        )
                    }
                }
            }
        }
        cacheJob = viewModelScope.launch {
            val cached = inventoryStore.current()
            if (cached.isNotEmpty()) {
                _state.update {
                    it.copy(
                        apps = cached,
                        // Cache-first paint: keep the list usable instead of a full-page spinner.
                        loading = false,
                    )
                }
            }
        }
        viewModelScope.launch {
            ruleRepository.current()
            // Heavy projection (rules + search + filter + sort) is decoupled from expand flags
            // so tapping a card header does not re-run RulesEngine over the full inventory.
            combine(
                ruleRepository.rules,
                state.map { Triple(it.apps, it.searchQuery, it.filters) }.distinctUntilChanged(),
                state.map { it.sort }.distinctUntilChanged(),
            ) { rules, searchTriple, sort ->
                ProjectionCore(
                    rules = rules,
                    apps = searchTriple.first,
                    query = searchTriple.second,
                    filters = searchTriple.third,
                    sort = sort,
                )
            }.collectLatest { core ->
                // LibChecker APP_LIST_UPDATE_DEBOUNCE: coalesce batch publishes during scan.
                // First projection after process start stays immediate so cache paint is fast.
                if (projectionEmitted) {
                    delay(PROJECTION_DEBOUNCE_MS)
                }
                projectionEmitted = true
                val plan = withContext(Dispatchers.Default) {
                    RulesEngine(core.rules).plan(core.apps.flatMap { it.channels })
                }
                val items = withContext(Dispatchers.Default) {
                    ChannelListProjector.project(
                        apps = core.apps,
                        query = core.query,
                        filters = core.filters,
                        sort = core.sort,
                        plan = plan,
                        expandedPackages = emptySet(),
                        autoExpandOnSearch = false,
                    )
                }
                _state.update {
                    if (it.apps != core.apps || ruleRepository.rules.value != core.rules) {
                        it
                    } else {
                        it.copy(
                            rulesCount = core.rules.size,
                            plan = plan,
                            baseListItems = items,
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            // Cheap expand remap: only swaps the expanded flag on existing items.
            combine(
                state.map { it.baseListItems }.distinctUntilChanged(),
                state.map { Pair(it.expandedPackages, it.searchQuery) }.distinctUntilChanged(),
            ) { items, expandPair ->
                items to expandPair
            }.collectLatest { (items, expandPair) ->
                val (expanded, query) = expandPair
                val autoExpand = query.isNotBlank()
                val projected = if (items.isEmpty()) {
                    items
                } else {
                    items.map { item ->
                        val shouldExpand = autoExpand || expanded.contains(item.app.packageName)
                        if (item.expanded == shouldExpand) item else item.copy(expanded = shouldExpand)
                    }
                }
                val visibleChannels = projected.sumOf { it.channels.size }
                val filtersActive = _state.value.filters.isActive
                val summary = if (query.isNotBlank() || filtersActive) {
                    "匹配 ${projected.size} 个应用 · $visibleChannels 个渠道"
                } else {
                    "${projected.size} 个应用 · $visibleChannels 个渠道"
                }
                _state.update { state ->
                    if (state.baseListItems !== items) state
                    else state.copy(listItems = projected, listSummary = summary)
                }
            }
        }
        viewModelScope.launch {
            muteUndo.snapshots.collect { snaps ->
                val latest = snaps.firstOrNull()
                _state.update {
                    it.copy(
                        canUndoLastBatch = latest != null && latest.entries.isNotEmpty(),
                        undoLabel = latest?.let { s -> "撤销 ${s.label}（${s.size}）" },
                    )
                }
            }
        }
        // First emission: cache-first bootstrap (InstallerX/LibChecker). Later changes re-probe.
        viewModelScope.launch {
            appSettings.preferredAuthorizer.collect { pref ->
                val previous = _state.value.preferredAuthorizer
                _state.update { it.copy(preferredAuthorizer = pref) }
                if (!authorizerBootstrapped) {
                    authorizerBootstrapped = true
                    bootstrapFromCache()
                } else if (previous != pref) {
                    refresh(forceInventory = true)
                }
            }
        }
    }

    private companion object {
        /**
         * Cold start paints the status card from the last successful privilege (SharedPreferences).
         * Avoids the gray CHECKING frame while DataStore + Root/Shizuku probes are still running.
         */
        fun seedHomeState(appSettings: AppSettings): HomeUiState {
            val seed = appSettings.lastKnownPrivilegeSync() ?: return HomeUiState()
            val caps = appSettings.capabilitiesSync()
            return HomeUiState(
                gate = PrivilegeGate.READY,
                checkingPrivilege = false,
                privilege = seed,
                shizukuAvailable = caps?.shizukuAvailable ?: false,
                shizukuAuthorized = caps?.shizukuAuthorized ?: false,
                dhizukuAvailable = caps?.dhizukuAvailable ?: false,
                rootAvailable = caps?.rootAvailable ?: false,
                rootLabel = caps?.rootLabel?.takeIf { it.isNotBlank() } ?: "无",
                rootDescription = caps?.rootDescription.orEmpty(),
                rootWriteSupported = caps?.rootWriteSupported ?: false,
            )
        }

        /** LibChecker doOnMainThreadIdle: run after the next frame so first paint wins. */
        suspend fun awaitMainThreadIdle() {
            suspendCancellableCoroutine<Unit> { cont ->
                val idle = android.os.MessageQueue.IdleHandler {
                    if (cont.isActive) cont.resume(Unit)
                    false
                }
                val queue = android.os.Looper.getMainLooper().queue
                queue.addIdleHandler(idle)
                cont.invokeOnCancellation { queue.removeIdleHandler(idle) }
            }
        }

        /**
         * Skip a full Binder channel rescan when disk inventory is still fresh.
         * Package add/remove remains incremental; user refresh always forces a full scan.
         */
        fun isInventoryFresh(scannedAt: Long, now: Long = System.currentTimeMillis()): Boolean {
            if (scannedAt <= 0L) return false
            return now - scannedAt < INVENTORY_FRESH_MS
        }

        const val INVENTORY_FRESH_MS = 6 * 60 * 60 * 1000L
        const val SCAN_CONCURRENCY = 6
        const val PUBLISH_BATCH = 20
        const val PUBLISH_INTERVAL_MS = 200L
        const val PROJECTION_DEBOUNCE_MS = 250L
    }

    private data class ProjectionCore(
        val rules: List<app.quieta.core.model.Rule>,
        val apps: List<AppChannels>,
        val query: String,
        val filters: ChannelListFilters,
        val sort: ChannelSort,
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
        val pkgs = _state.value.baseListItems.map { it.app.packageName }.toSet()
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

    fun refresh() = refresh(forceInventory = true)

    /**
     * Cache-first bootstrap: paint green + list immediately, probe privilege in the background,
     * and skip a full Binder channel scan when disk inventory is still fresh.
     */
    private fun bootstrapFromCache() {
        viewModelScope.launch {
            cacheJob.join()
            val hasCache = _state.value.apps.isNotEmpty()
            val fresh = isInventoryFresh(appSettings.inventoryScannedAt())
            if (hasCache && fresh) {
                // InstallerX-style: keep last green status; only background-probe, no full rescan.
                refresh(forceInventory = false)
            } else {
                refresh(forceInventory = true)
            }
        }
    }

    private fun refresh(forceInventory: Boolean) {
        refreshJob?.cancel()
        if (forceInventory) {
            inventoryJob?.cancel()
        }
        // Preserve InstallerX green card: never regress a known-good READY status to gray/red
        // just because a background probe is still running.
        val keepGreen = _state.value.gate == PrivilegeGate.READY && _state.value.privilege.available
        val hasApps = _state.value.apps.isNotEmpty()
        refreshJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    checkingPrivilege = true,
                    // Only show a page spinner when there is nothing cached to display.
                    loading = forceInventory && !hasApps,
                    error = null,
                    gate = if (keepGreen) PrivilegeGate.READY else it.gate,
                )
            }
            val pref = _state.value.preferredAuthorizer
            val rootProbe = async(Dispatchers.IO) { rootBackend.probeCapabilities() }
            val shizukuProbe = async(Dispatchers.IO) { CapabilityProbe.probeShizuku() }
            val dhizukuProbe = async(Dispatchers.IO) { dhizukuBackend.isAvailable() }
            val shizuku = shizukuProbe.await()
            val dhizukuOk = dhizukuProbe.await()
            val shizukuOk = shizuku.binderAlive && shizuku.permissionGranted

            // InstallerX: paint READY from the fast path. Root `su` must not block the
            // first list/status when Shizuku/Dhizuku already work (unless user pinned ROOT).
            var inventoryStarted = false
            val rootPinned = pref == app.quieta.core.settings.PreferredAuthorizer.ROOT
            if (!rootPinned && (shizukuOk || dhizukuOk)) {
                val fastId = when {
                    shizukuOk && pref != app.quieta.core.settings.PreferredAuthorizer.DHIZUKU ->
                        PrivilegeId.SHIZUKU
                    else -> PrivilegeId.DHIZUKU
                }
                backend = when (fastId) {
                    PrivilegeId.SHIZUKU -> shizukuBackend
                    else -> dhizukuBackend
                }
                val label = if (fastId == PrivilegeId.SHIZUKU) "Shizuku" else "Dhizuku"
                _state.update {
                    it.copy(
                        checkingPrivilege = false,
                        shizukuAvailable = shizuku.binderAlive,
                        shizukuAuthorized = shizukuOk,
                        dhizukuAvailable = dhizukuOk,
                        gate = PrivilegeGate.READY,
                        error = null,
                        privilege = PrivilegeStatus(id = fastId, available = true, label = label),
                    )
                }
                appSettings.setLastKnownPrivilege(
                    PrivilegeStatus(fastId, available = true, label = label),
                )
                if (forceInventory || _state.value.apps.isEmpty()) {
                    awaitMainThreadIdle()
                    startInventoryRefresh(showLoading = !hasApps)
                    inventoryStarted = true
                } else {
                    _state.update { it.copy(loading = false, progress = null) }
                }
            }

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

            val capabilityUpdate: (HomeUiState) -> HomeUiState = {
                it.copy(
                    checkingPrivilege = false,
                    shizukuAvailable = shizuku.binderAlive,
                    shizukuAuthorized = shizukuOk,
                    dhizukuAvailable = dhizukuOk,
                    rootAvailable = rootOk,
                    rootLabel = rootLabel,
                    rootDescription = rootDescription,
                    rootWriteSupported = rootCapabilities.writeSupported,
                )
            }
            appSettings.saveCapabilities(
                app.quieta.core.settings.CachedCapabilities(
                    shizukuAvailable = shizuku.binderAlive,
                    shizukuAuthorized = shizukuOk,
                    dhizukuAvailable = dhizukuOk,
                    rootAvailable = rootOk,
                    rootLabel = rootLabel,
                    rootDescription = rootDescription,
                    rootWriteSupported = rootCapabilities.writeSupported,
                ),
            )

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
                if (!inventoryStarted && (forceInventory || _state.value.apps.isEmpty())) {
                    awaitMainThreadIdle()
                    startInventoryRefresh(showLoading = !hasApps)
                } else if (!inventoryStarted) {
                    _state.update { it.copy(loading = false, progress = null) }
                }
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
                    val ai = runCatching { appContext.packageManager.getApplicationInfo(packageName, 0) }.getOrNull()
                    val name = runCatching {
                        appContext.packageManager.getApplicationLabel(ai!!).toString()
                    }.getOrDefault(packageName)
                    val system = ai != null &&
                        (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    name to system
                }
                val item = AppChannels(
                    packageName = packageName,
                    appLabel = label.first,
                    channels = channels,
                    isSystem = label.second,
                )
                inventoryStore.upsert(item)
                val next = _state.value.apps.filterNot { it.packageName == packageName } + item
                publishApps(sortApps(next), loading = _state.value.loading)
            }
        }
    }

    /** Opens a dry-run preview instead of writing immediately. */
    fun requestBatchMutePreview() {
        viewModelScope.launch {
            if (_state.value.checkingPrivilege || _state.value.gate != PrivilegeGate.READY) return@launch
            val rules = ruleRepository.current()
            val scope = _state.value.muteScope
            val apps = resolveScopeApps(scope)
            val engine = RulesEngine(rules)
            val targets = withContext(Dispatchers.Default) {
                apps.flatMap { app ->
                    app.channels.mapNotNull { channel ->
                        val decision = engine.decisionFor(channel)
                        if (decision.action == RuleAction.MUTE || decision.action == RuleAction.DOWNGRADE) {
                            MutePreviewItem(
                                packageName = channel.packageName,
                                appLabel = app.appLabel,
                                channelName = channel.name,
                                channelId = channel.id,
                                action = decision.action,
                                reason = decision.reason,
                            )
                        } else null
                    }
                }.sortedWith(compareBy({ it.packageName }, { it.channelId }))
            }
            _state.update {
                it.copy(
                    mutePreview = MutePreview(
                        items = targets,
                        muteCount = targets.count { p -> p.action == RuleAction.MUTE },
                        downgradeCount = targets.count { p -> p.action == RuleAction.DOWNGRADE },
                        scope = scope,
                        filterActive = it.searchQuery.isNotBlank() || it.filters.isActive,
                    ),
                    muteResult = null,
                )
            }
        }
    }

    fun setMuteScope(scope: MuteScope) {
        _state.update { it.copy(muteScope = scope) }
        // Rebuild preview if open so the list matches the new scope.
        if (_state.value.mutePreview != null) {
            requestBatchMutePreview()
        }
    }

    fun dismissMutePreview() {
        _state.update { it.copy(mutePreview = null) }
    }

    fun confirmBatchMute() {
        viewModelScope.launch {
            val preview = _state.value.mutePreview
            if (_state.value.checkingPrivilege || _state.value.gate != PrivilegeGate.READY || preview == null) {
                _state.update { it.copy(mutePreview = null) }
                return@launch
            }
            val activeBackend = backend
            val plan = preview.items.associate { item ->
                Channel(
                    packageName = item.packageName,
                    id = item.channelId,
                    name = item.channelName,
                    importance = ChannelImportance.DEFAULT,
                ) to item.action
            }
            val fullPlan = withContext(Dispatchers.Default) {
                RulesEngine(ruleRepository.current()).plan(_state.value.apps.flatMap { it.channels })
            }
            _state.update { it.copy(plan = fullPlan, mutePreview = null) }
            if (plan.isEmpty()) {
                _state.update { it.copy(muteResult = MuteResult(0, 0, 0, listOf("没有可执行的规则"))) }
                return@launch
            }
            _state.update { it.copy(progress = "正在批量静音…", muteResult = null) }
            val result = withContext(Dispatchers.Default) {
                BatchMuteUseCase(activeBackend).apply(plan)
            }
            if (activeBackend.id == PrivilegeId.ROOT && backend === activeBackend) {
                _state.update {
                    it.copy(
                        rootDescription = it.rootDescription.substringBeforeLast('\n') + "\n" +
                            appContext.getString(
                                if (result.failed == 0 && result.success > 0) {
                                    app.quieta.R.string.privilege_root_write_verified
                                } else {
                                    app.quieta.R.string.privilege_root_write_failed
                                },
                            ),
                    )
                }
            }
            val scopeLabel = when (preview.scope) {
                MuteScope.ALL -> "全部命中"
                MuteScope.FILTERED -> "当前筛选"
            }
            val channelByKey = _state.value.apps
                .flatMap { app -> app.channels }
                .associateBy { ch -> ch.packageName + "|" + ch.id }
            val snapshotEntries = preview.items.mapNotNull { item ->
                val ch = channelByKey[item.packageName + "|" + item.channelId] ?: return@mapNotNull null
                MuteSnapshotEntry(
                    packageName = item.packageName,
                    channelId = item.channelId,
                    channelName = item.channelName,
                    previousImportance = MuteUndoStore.importanceToInt(ch.importance),
                    newImportance = if (item.action == RuleAction.MUTE) 0 else 2,
                )
            }
            if (snapshotEntries.isNotEmpty()) {
                muteUndo.push(
                    MuteSnapshot(
                        label = "按规则静音（$scopeLabel）",
                        time = MuteUndoStore.now(),
                        entries = snapshotEntries,
                    ),
                )
            }
            muteLog.append(
                app.quieta.core.engine.MuteLogEntry(
                    label = "按规则静音（$scopeLabel）",
                    detail = "成功 ${result.success} / ${result.total}，失败 ${result.failed}",
                    time = app.quieta.core.engine.MuteLogStore.now(),
                    tag = if (result.failed == 0) "成功" else "部分失败",
                ),
            )
            _state.update { it.copy(progress = null, muteResult = result) }
            startInventoryRefresh()
        }
    }

    fun undoLastBatch() {
        viewModelScope.launch {
            if (_state.value.checkingPrivilege || _state.value.gate != PrivilegeGate.READY) return@launch
            val snap = muteUndo.latest() ?: return@launch
            if (snap.entries.isEmpty()) return@launch
            val activeBackend = backend
            _state.update { it.copy(progress = "正在撤销…", muteResult = null) }
            val result = withContext(Dispatchers.Default) {
                ChannelActionUseCase(activeBackend).restoreEntries(snap.entries)
            }
            muteUndo.drop(snap.id)
            muteLog.append(
                app.quieta.core.engine.MuteLogEntry(
                    label = "撤销批次",
                    detail = snap.label + " · 成功 ${result.success} / ${result.total}",
                    time = app.quieta.core.engine.MuteLogStore.now(),
                    tag = if (result.failed == 0) "成功" else "部分失败",
                ),
            )
            _state.update { it.copy(progress = null, muteResult = result) }
            startInventoryRefresh()
        }
    }

    fun muteApp(packageName: String) {
        applyAppAction(packageName, RuleAction.MUTE, label = "整应用静音")
    }

    fun restoreApp(packageName: String) {
        applyAppAction(packageName, RuleAction.KEEP, label = "整应用恢复")
    }

    private fun applyAppAction(packageName: String, action: RuleAction, label: String) {
        viewModelScope.launch {
            if (_state.value.checkingPrivilege || _state.value.gate != PrivilegeGate.READY) return@launch
            val targetApp = _state.value.apps.firstOrNull { it.packageName == packageName } ?: return@launch
            val activeBackend = backend
            _state.update { it.copy(progress = "正在处理 $label…", muteResult = null) }
            if (action == RuleAction.MUTE) {
                val entries = targetApp.channels.map { ch ->
                    MuteSnapshotEntry(
                        packageName = ch.packageName,
                        channelId = ch.id,
                        channelName = ch.name,
                        previousImportance = MuteUndoStore.importanceToInt(ch.importance),
                        newImportance = 0,
                    )
                }
                if (entries.isNotEmpty()) {
                    muteUndo.push(
                        MuteSnapshot(
                            label = label + " · " + targetApp.appLabel,
                            time = MuteUndoStore.now(),
                            entries = entries,
                        ),
                    )
                }
            }
            val result = withContext(Dispatchers.Default) {
                ChannelActionUseCase(activeBackend).applyToApp(targetApp.channels, action)
            }
            muteLog.append(
                app.quieta.core.engine.MuteLogEntry(
                    label = label,
                    detail = targetApp.packageName + " · 成功 ${result.success} / ${result.total}",
                    time = app.quieta.core.engine.MuteLogStore.now(),
                    tag = if (result.failed == 0) "成功" else "部分失败",
                ),
            )
            _state.update { it.copy(progress = null, muteResult = result) }
            startInventoryRefresh()
        }
    }

    private fun resolveScopeApps(scope: MuteScope): List<AppChannels> {
        return when (scope) {
            MuteScope.ALL -> _state.value.apps
            MuteScope.FILTERED -> _state.value.baseListItems.map { it.app }
        }
    }

    fun applyChannelAction(channel: Channel, action: RuleAction) {
        viewModelScope.launch {
            if (_state.value.checkingPrivilege || _state.value.gate != PrivilegeGate.READY) return@launch
            val activeBackend = backend
            val ok = withContext(Dispatchers.Default) {
                ChannelActionUseCase(activeBackend).apply(channel, action)
            }
            val label = when (action) {
                RuleAction.MUTE -> "手动静音"
                RuleAction.DOWNGRADE -> "手动降级"
                RuleAction.KEEP -> "手动恢复"
            }
            muteLog.append(
                app.quieta.core.engine.MuteLogEntry(
                    label = label,
                    detail = channel.packageName + "/" + channel.id + if (ok.isSuccess) " 成功" else " 失败",
                    time = app.quieta.core.engine.MuteLogStore.now(),
                    tag = if (ok.isSuccess) "成功" else "失败",
                ),
            )
            _state.update {
                it.copy(
                    muteResult = MuteResult(
                        total = 1,
                        success = if (ok.isSuccess) 1 else 0,
                        failed = if (ok.isSuccess) 0 else 1,
                        errors = if (ok.isSuccess) emptyList() else listOf(ok.exceptionOrNull()?.message ?: "unknown"),
                    ),
                    mutePreview = null,
                )
            }
            startInventoryRefresh()
        }
    }

    /**
     * Show disk cache first, then scan with limited concurrency and publish in batches
     * so the UI stays scrollable (LibChecker InitializeAppListUseCase pattern).
     */
    private fun startInventoryRefresh(showLoading: Boolean = true) {
        inventoryJob?.cancel()
        val activeBackend = backend
        inventoryJob = viewModelScope.launch {
            cacheJob.join()
            val cached = withContext(Dispatchers.IO) { inventoryStore.current() }
            if (cached.isNotEmpty()) {
                publishApps(
                    cached,
                    loading = showLoading,
                    progress = if (showLoading) "刷新通知渠道…" else null,
                )
            } else {
                _state.update { it.copy(loading = true, progress = "读取应用列表…") }
            }

            runCatching {
                ruleRepository.current()
                val baseApps = withContext(Dispatchers.IO) {
                    InstalledApps.load(
                        context = appContext,
                        known = cached.associateBy { it.packageName },
                    )
                }
                val results = HashMap<String, AppChannels>(baseApps.size + cached.size)
                val systemByPkg = baseApps.associate { it.packageName to it.isSystem }
                cached.forEach { item ->
                    results[item.packageName] = item.copy(
                        isSystem = systemByPkg[item.packageName] ?: item.isSystem,
                    )
                }

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
                    appSettings.markInventoryScanned()
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
}
