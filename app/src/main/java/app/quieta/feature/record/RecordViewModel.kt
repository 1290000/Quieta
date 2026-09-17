package app.quieta.feature.record

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quieta.core.engine.ChannelActionUseCase
import app.quieta.core.engine.MuteLogEntry
import app.quieta.core.engine.MuteLogStore
import app.quieta.core.engine.NotificationTimelineEntry
import app.quieta.core.engine.NotificationTimelineStore
import app.quieta.core.model.AppChannels
import app.quieta.core.model.Channel
import app.quieta.core.model.ChannelImportance
import app.quieta.core.model.RuleAction
import app.quieta.core.privilege.PrivilegeBackends
import app.quieta.core.repo.ChannelInventoryStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RecordSource { ALL, TIMELINE, MUTE_LOG }
enum class RecordTimeRange { ALL, TODAY, LAST_7_DAYS }
enum class RecordSort { TIME_DESC, COUNT_DESC, APP_NAME }

/** LibChecker-style density knobs for the record list. */
data class RecordDisplayPrefs(
    val showAppIcon: Boolean = true,
    val showAppName: Boolean = true,
    val showPackageName: Boolean = true,
    val showChannelName: Boolean = true,
    val showChannelId: Boolean = false,
    val showImportance: Boolean = true,
    val showSoundDot: Boolean = true,
    val showVibration: Boolean = false,
    val showTimeRange: Boolean = true,
    val showCount: Boolean = true,
    val showPrivacyNote: Boolean = true,
)

data class RecordFilters(
    val source: RecordSource = RecordSource.ALL,
    val timeRange: RecordTimeRange = RecordTimeRange.ALL,
    val sort: RecordSort = RecordSort.TIME_DESC,
    val query: String = "",
    /** Empty = all apps. */
    val packageFilter: String? = null,
    /** -1 = all. */
    val minImportance: Int = -1,
    val soundOnOnly: Boolean = false,
    val soundOffOnly: Boolean = false,
) {
    val isActive: Boolean
        get() = source != RecordSource.ALL ||
            timeRange != RecordTimeRange.ALL ||
            sort != RecordSort.TIME_DESC ||
            query.isNotBlank() ||
            packageFilter != null ||
            minImportance >= 0 ||
            soundOnOnly ||
            soundOffOnly
}

data class TimelineDayGroup(
    val dayLabel: String,
    val apps: List<TimelineAppGroup>,
    val total: Int,
)

data class TimelineAppGroup(
    val packageName: String,
    val appLabel: String,
    val channels: List<TimelineItem>,
    val expanded: Boolean = false,
)

data class TimelineItem(
    val id: String,
    val packageName: String,
    val appLabel: String,
    val channelId: String,
    val channelName: String,
    val timeRange: String,
    val timestamp: Long,
    val count: Int,
    val importanceLabel: String,
    val importance: Int,
    val soundEnabled: Boolean? = null,
    val vibrationEnabled: Boolean? = null,
)

data class TimelineSummary(
    val todayCount: Int = 0,
    val topApps: List<Pair<String, Int>> = emptyList(),
    val topChannels: List<Pair<String, Int>> = emptyList(),
)

class RecordViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application
    private val store = MuteLogStore.getInstance(application)
    private val timelineStore = NotificationTimelineStore.getInstance(application)
    private val inventoryStore = ChannelInventoryStore.getInstance(application)
    private val prefs = context.getSharedPreferences("record_display", Context.MODE_PRIVATE)

    private val collapsedApps = MutableStateFlow<Set<String>>(emptySet())
    private val filtersFlow = MutableStateFlow(RecordFilters())
    private val displayFlow = MutableStateFlow(loadDisplayPrefs())

    val filters: StateFlow<RecordFilters> = filtersFlow
    val displayPrefs: StateFlow<RecordDisplayPrefs> = displayFlow

    val items: StateFlow<List<RecordItem>> = combine(store.entries, filtersFlow) { list, filters ->
        if (filters.source == RecordSource.TIMELINE) return@combine emptyList()
        val q = filters.query.trim()
        list.filter { log ->
            if (q.isEmpty()) true
            else log.label.contains(q, true) || log.detail.contains(q, true) || log.tag.contains(q, true)
        }.map { RecordItem(it.id, it.label, it.detail, it.time, it.tag) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val dayGroupsFlow = combine(
        timelineStore.entries,
        inventoryStore.snapshot,
        collapsedApps,
        filtersFlow,
    ) { args: Array<*> ->
        @Suppress("UNCHECKED_CAST")
        val entries = args[0] as List<NotificationTimelineEntry>
        @Suppress("UNCHECKED_CAST")
        val inventory = args[1] as List<AppChannels>
        @Suppress("UNCHECKED_CAST")
        val collapsed = args[2] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val filters = args[3] as RecordFilters
        buildDayGroups(enrich(entries, inventory), collapsed, filters)
    }

    val dayGroups: StateFlow<List<TimelineDayGroup>> = dayGroupsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary: StateFlow<TimelineSummary> = combine(
        timelineStore.entries,
        inventoryStore.snapshot,
    ) { entries, inventory -> buildSummary(enrich(entries, inventory)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineSummary())

    var actionMessage: String? = null
        private set

    fun toggleApp(packageName: String) {
        collapsedApps.update { current ->
            if (packageName in current) current - packageName else current + packageName
        }
    }

    fun expandAll() {
        viewModelScope.launch {
            val pkgs = dayGroups.value.flatMap { g -> g.apps.map { it.packageName } }.toSet()
            collapsedApps.update { pkgs } // all in collapsed → invert below
            // collapsedApps means collapsed; empty = all expanded. Clear = expand all.
            collapsedApps.update { emptySet() }
        }
    }

    fun collapseAll() {
        viewModelScope.launch {
            val pkgs = dayGroups.value.flatMap { g -> g.apps.map { it.packageName } }.toSet()
            collapsedApps.update { pkgs }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            store.clear()
            timelineStore.clear()
        }
    }

    fun clearTimelineOnly() {
        viewModelScope.launch { timelineStore.clear() }
    }

    fun clearMuteLogOnly() {
        viewModelScope.launch { store.clear() }
    }

    fun setSource(source: RecordSource) = filtersFlow.update { it.copy(source = source) }

    fun setTimeRange(range: RecordTimeRange) = filtersFlow.update { it.copy(timeRange = range) }

    fun setSort(sort: RecordSort) = filtersFlow.update { it.copy(sort = sort) }

    fun setQuery(query: String) = filtersFlow.update { it.copy(query = query) }

    fun setPackageFilter(packageName: String?) = filtersFlow.update { it.copy(packageFilter = packageName) }

    fun setMinImportance(value: Int) = filtersFlow.update { it.copy(minImportance = value) }

    fun setSoundFilter(onOnly: Boolean, offOnly: Boolean) = filtersFlow.update {
        it.copy(soundOnOnly = onOnly, soundOffOnly = offOnly)
    }

    fun resetFilters() = filtersFlow.update { RecordFilters() }

    fun toggleDisplay(pref: String) {
        displayFlow.update { prefs ->
            val next = when (pref) {
                "appIcon" -> prefs.copy(showAppIcon = !prefs.showAppIcon)
                "appName" -> prefs.copy(showAppName = !prefs.showAppName)
                "packageName" -> prefs.copy(showPackageName = !prefs.showPackageName)
                "channelName" -> prefs.copy(showChannelName = !prefs.showChannelName)
                "channelId" -> prefs.copy(showChannelId = !prefs.showChannelId)
                "importance" -> prefs.copy(showImportance = !prefs.showImportance)
                "soundDot" -> prefs.copy(showSoundDot = !prefs.showSoundDot)
                "vibration" -> prefs.copy(showVibration = !prefs.showVibration)
                "timeRange" -> prefs.copy(showTimeRange = !prefs.showTimeRange)
                "count" -> prefs.copy(showCount = !prefs.showCount)
                "privacyNote" -> prefs.copy(showPrivacyNote = !prefs.showPrivacyNote)
                else -> prefs
            }
            saveDisplayPrefs(next)
            next
        }
    }

    fun resetDisplayPrefs() {
        val defaults = RecordDisplayPrefs()
        saveDisplayPrefs(defaults)
        displayFlow.value = defaults
    }

    fun applyChannelAction(item: TimelineItem, action: RuleAction) {
        viewModelScope.launch {
            val backend = PrivilegeBackends.preferred(context) ?: run {
                actionMessage = "无可用提权后端"
                return@launch
            }
            val channel = Channel(
                packageName = item.packageName,
                id = item.channelId,
                name = item.channelName.ifBlank { item.channelId },
                importance = when (item.importance) {
                    0 -> ChannelImportance.NONE
                    1 -> ChannelImportance.MIN
                    2 -> ChannelImportance.LOW
                    4, 5 -> ChannelImportance.HIGH
                    else -> ChannelImportance.DEFAULT
                },
            )
            val result = withContext(Dispatchers.Default) {
                ChannelActionUseCase(backend).apply(channel, action)
            }
            actionMessage = if (result.isSuccess) {
                "已${when (action) {
                    RuleAction.MUTE -> "静音"
                    RuleAction.DOWNGRADE -> "降级"
                    RuleAction.KEEP -> "恢复"
                }} ${item.channelName.ifBlank { item.channelId }}"
            } else {
                "操作失败：${result.exceptionOrNull()?.message ?: "unknown"}"
            }
            store.append(
                MuteLogEntry(
                    label = when (action) {
                        RuleAction.MUTE -> "时间线静音"
                        RuleAction.DOWNGRADE -> "时间线降级"
                        RuleAction.KEEP -> "时间线恢复"
                    },
                    detail = item.packageName + "/" + item.channelId,
                    time = MuteLogStore.now(),
                    tag = if (result.isSuccess) "成功" else "失败",
                ),
            )
        }
    }

    private fun loadDisplayPrefs(): RecordDisplayPrefs {
        fun b(key: String, default: Boolean) = prefs.getBoolean(key, default)
        return RecordDisplayPrefs(
            showAppIcon = b("appIcon", true),
            showAppName = b("appName", true),
            showPackageName = b("packageName", true),
            showChannelName = b("channelName", true),
            showChannelId = b("channelId", false),
            showImportance = b("importance", true),
            showSoundDot = b("soundDot", true),
            showVibration = b("vibration", false),
            showTimeRange = b("timeRange", true),
            showCount = b("count", true),
            showPrivacyNote = b("privacyNote", true),
        )
    }

    private fun saveDisplayPrefs(p: RecordDisplayPrefs) {
        prefs.edit()
            .putBoolean("appIcon", p.showAppIcon)
            .putBoolean("appName", p.showAppName)
            .putBoolean("packageName", p.showPackageName)
            .putBoolean("channelName", p.showChannelName)
            .putBoolean("channelId", p.showChannelId)
            .putBoolean("importance", p.showImportance)
            .putBoolean("soundDot", p.showSoundDot)
            .putBoolean("vibration", p.showVibration)
            .putBoolean("timeRange", p.showTimeRange)
            .putBoolean("count", p.showCount)
            .putBoolean("privacyNote", p.showPrivacyNote)
            .apply()
    }

    private fun enrich(
        entries: List<NotificationTimelineEntry>,
        inventory: List<AppChannels>,
    ): List<NotificationTimelineEntry> {
        val byKey = inventory.flatMap { app -> app.channels.map { ch -> (app.packageName to ch.id) to ch } }.toMap()
        return entries.map { e ->
            val ch = byKey[e.packageName to e.channelId] ?: return@map e
            e.copy(
                appLabel = e.appLabel.ifBlank { appLabelOf(e.packageName, inventory) },
                channelName = e.channelName.ifBlank { ch.name },
                importance = if (e.importance < 0) ch.importance.toInt() else e.importance,
                soundEnabled = e.soundEnabled ?: ch.soundEnabled,
                vibrationEnabled = e.vibrationEnabled ?: ch.vibrationEnabled,
            )
        }
    }

    private fun appLabelOf(packageName: String, inventory: List<AppChannels>): String =
        inventory.firstOrNull { it.packageName == packageName }?.appLabel ?: packageName

    private fun ChannelImportance.toInt(): Int = when (this) {
        ChannelImportance.NONE -> 0
        ChannelImportance.MIN -> 1
        ChannelImportance.LOW -> 2
        ChannelImportance.DEFAULT -> 3
        ChannelImportance.HIGH -> 4
    }

    private fun buildDayGroups(
        entries: List<NotificationTimelineEntry>,
        collapsed: Set<String>,
        filters: RecordFilters,
    ): List<TimelineDayGroup> {
        val filtered = entries.filter { e ->
            if (filters.source == RecordSource.MUTE_LOG) return@filter false
            if (filters.timeRange != RecordTimeRange.ALL) {
                val start = when (filters.timeRange) {
                    RecordTimeRange.TODAY -> startOfToday()
                    RecordTimeRange.LAST_7_DAYS -> System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                    RecordTimeRange.ALL -> 0L
                }
                if (e.lastAt < start) return@filter false
            }
            val pkg = filters.packageFilter
            if (pkg != null && e.packageName != pkg) return@filter false
            if (filters.minImportance >= 0 && e.importance != filters.minImportance) return@filter false
            if (filters.soundOnOnly || filters.soundOffOnly) {
                val sound = e.soundEnabled
                if (filters.soundOnOnly && sound == false) return@filter false
                if (filters.soundOffOnly && sound == true) return@filter false
            }
            val q = filters.query.trim()
            if (q.isNotEmpty()) {
                val hit = e.appLabel.contains(q, true) ||
                    e.packageName.contains(q, true) ||
                    e.channelName.contains(q, true) ||
                    e.channelId.contains(q, true)
                if (!hit) return@filter false
            }
            true
        }

        val dayFmt = SimpleDateFormat("M月d日", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val sorted = when (filters.sort) {
            RecordSort.TIME_DESC -> filtered.sortedByDescending { it.lastAt }
            RecordSort.COUNT_DESC -> filtered.sortedByDescending { it.count }
            RecordSort.APP_NAME -> filtered.sortedWith(compareBy({ it.appLabel.lowercase() }, { -it.lastAt }))
        }
        val byDay = sorted.groupBy { dayLabel(it.lastAt, dayFmt) }
        return byDay.map { (day, dayEntries) ->
            val apps = dayEntries
                .groupBy { it.packageName }
                .map { (pkg, list) ->
                    TimelineAppGroup(
                        packageName = pkg,
                        appLabel = list.first().appLabel.ifBlank { pkg },
                        expanded = pkg !in collapsed,
                        channels = list.map { e -> toItem(e, timeFmt) },
                    )
                }
                .let { groups ->
                    when (filters.sort) {
                        RecordSort.COUNT_DESC ->
                            groups.sortedByDescending { g -> g.channels.sumOf { it.count } }
                        RecordSort.APP_NAME ->
                            groups.sortedBy { it.appLabel.lowercase() }
                        RecordSort.TIME_DESC -> groups
                    }
                }
            TimelineDayGroup(day, apps, apps.sumOf { g -> g.channels.sumOf { it.count } })
        }
    }

    private fun toItem(e: NotificationTimelineEntry, timeFmt: SimpleDateFormat): TimelineItem {
        return TimelineItem(
            id = e.id,
            packageName = e.packageName,
            appLabel = e.appLabel.ifBlank { e.packageName },
            channelId = e.channelId,
            channelName = e.channelName.ifBlank { e.channelId },
            timeRange = formatRange(e, timeFmt),
            timestamp = e.lastAt,
            count = e.count,
            importanceLabel = importanceLabel(e.importance),
            importance = e.importance,
            soundEnabled = e.soundEnabled,
            vibrationEnabled = e.vibrationEnabled,
        )
    }

    private fun formatRange(e: NotificationTimelineEntry, fmt: SimpleDateFormat): String {
        val first = fmt.format(Date(e.firstAt))
        val last = fmt.format(Date(e.lastAt))
        return if (e.firstAt == e.lastAt || e.count <= 1) last else "$first–$last"
    }

    private fun importanceLabel(importance: Int): String = when (importance) {
        0 -> "NONE"
        1 -> "MIN"
        2 -> "LOW"
        3 -> "DEFAULT"
        4, 5 -> "HIGH"
        else -> "—"
    }

    private fun dayLabel(timestamp: Long, fmt: SimpleDateFormat): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = timestamp }
        val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        return if (sameDay) "今天" else fmt.format(Date(timestamp))
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun buildSummary(entries: List<NotificationTimelineEntry>): TimelineSummary {
        if (entries.isEmpty()) return TimelineSummary()
        val today = entries.filter { it.lastAt >= startOfToday() }
        val topApps = today
            .groupBy { it.packageName to it.appLabel.ifBlank { it.packageName } }
            .map { (key, list) -> key.second to list.sumOf { it.count } }
            .sortedByDescending { it.second }
            .take(3)
        val topChannels = today
            .groupBy { it.packageName to it.channelId }
            .map { (key, list) ->
                val label = list.first().channelName.ifBlank { key.second }
                val app = list.first().appLabel.ifBlank { key.first }
                "$app / $label" to list.sumOf { it.count }
            }
            .sortedByDescending { it.second }
            .take(3)
        return TimelineSummary(today.sumOf { it.count }, topApps, topChannels)
    }
}
