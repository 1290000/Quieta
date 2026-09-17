package app.quieta.feature.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quieta.core.engine.ChannelActionUseCase
import app.quieta.core.engine.MuteLogEntry
import app.quieta.core.engine.MuteLogStore
import app.quieta.core.engine.NotificationTimelineEntry
import app.quieta.core.engine.NotificationTimelineStore
import app.quieta.core.model.Channel
import app.quieta.core.model.ChannelImportance
import app.quieta.core.model.RuleAction
import app.quieta.core.privilege.PrivilegeBackends
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

data class TimelineDayGroup(
    val dayLabel: String,
    val apps: List<TimelineAppGroup>,
    val total: Int,
)

data class TimelineAppGroup(
    val packageName: String,
    val appLabel: String,
    val channels: List<TimelineItem>,
    val expanded: Boolean = true,
)

data class TimelineItem(
    val id: String,
    val packageName: String,
    val appLabel: String,
    val channelId: String,
    val channelName: String,
    val timeRange: String,
    val count: Int,
    val importanceLabel: String,
    val importance: Int,
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
    private val collapsedApps = MutableStateFlow<Set<String>>(emptySet())

    val items: StateFlow<List<RecordItem>> = store.entries
        .map { list ->
            list.map {
                RecordItem(
                    id = it.id,
                    title = it.label,
                    subtitle = it.detail,
                    time = it.time,
                    tag = it.tag,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val dayGroupsFlow = combine(timelineStore.entries, collapsedApps) { entries, collapsed ->
        buildDayGroups(entries, collapsed)
    }

    val dayGroups: StateFlow<List<TimelineDayGroup>> = dayGroupsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary: StateFlow<TimelineSummary> = timelineStore.entries
        .map { buildSummary(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineSummary())

    var actionMessage: String? = null
        private set

    fun toggleApp(packageName: String) {
        collapsedApps.update { current ->
            if (packageName in current) current - packageName else current + packageName
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            store.clear()
            timelineStore.clear()
        }
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

    private fun buildDayGroups(
        entries: List<NotificationTimelineEntry>,
        collapsed: Set<String>,
    ): List<TimelineDayGroup> {
        val dayFmt = SimpleDateFormat("M月d日", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val byDay = entries.groupBy { dayLabel(it.lastAt, dayFmt) }
        return byDay.map { (day, dayEntries) ->
            val apps = dayEntries
                .groupBy { it.packageName }
                .map { (pkg, list) ->
                    TimelineAppGroup(
                        packageName = pkg,
                        appLabel = list.first().appLabel.ifBlank { pkg },
                        expanded = pkg !in collapsed,
                        channels = list.map { e ->
                            TimelineItem(
                                id = e.id,
                                packageName = e.packageName,
                                appLabel = e.appLabel.ifBlank { e.packageName },
                                channelId = e.channelId,
                                channelName = e.channelName.ifBlank { e.channelId },
                                timeRange = formatRange(e, timeFmt),
                                count = e.count,
                                importanceLabel = importanceLabel(e.importance),
                                importance = e.importance,
                            )
                        },
                    )
                }
                .sortedByDescending { group -> group.channels.sumOf { it.count } }
            TimelineDayGroup(
                dayLabel = day,
                apps = apps,
                total = apps.sumOf { app -> app.channels.sumOf { it.count } },
            )
        }
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

    private fun buildSummary(entries: List<NotificationTimelineEntry>): TimelineSummary {
        if (entries.isEmpty()) return TimelineSummary()
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val today = entries.filter { it.lastAt >= todayStart }
        val todayCount = today.sumOf { it.count }
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
        return TimelineSummary(
            todayCount = todayCount,
            topApps = topApps,
            topChannels = topChannels,
        )
    }
}
