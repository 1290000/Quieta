package app.quieta.feature.record

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quieta.R
import app.quieta.core.model.RuleAction
import app.quieta.ui.component.HyperOsPopup
import app.quieta.ui.component.HyperOsPopupDivider
import app.quieta.ui.component.HyperOsPopupRow
import app.quieta.ui.component.PressableCard
import app.quieta.ui.component.QuietaPage
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class RecordItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val time: String,
    val tag: String,
)

@Composable
fun RecordScreen(
    modifier: Modifier = Modifier,
    blurEnabled: Boolean = true,
    viewModel: RecordViewModel = viewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val dayGroups by viewModel.dayGroups.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val display by viewModel.displayPrefs.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val listenerContext = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(Unit) {
        app.quieta.service.NotificationListenerAccess.ensureBound(
            listenerContext,
            "record_page",
        )
    }

    var actionTarget by remember { mutableStateOf<TimelineItem?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showDisplaySheet by remember { mutableStateOf(false) }

    if (selection.mode) {
        BackHandler { viewModel.exitSelection() }
    }

    QuietaPage(
        title = if (selection.mode) {
            if (selection.selectedKeys.isEmpty()) "多选渠道" else "已选择 ${selection.selectedKeys.size} 项"
        } else {
            stringResource(R.string.record_title)
        },
        modifier = modifier,
        blurEnabled = blurEnabled,
        navigationIcon = if (selection.mode) {
            {
                IconButton(onClick = viewModel::exitSelection) {
                    Icon(Icons.Outlined.Close, contentDescription = "退出多选")
                }
            }
        } else {
            {}
        },
        actions = {
            if (selection.mode) {
                TextButton(onClick = viewModel::selectAllVisible) { Text("全选") }
                TextButton(onClick = viewModel::clearSelection) { Text("清空") }
            } else {
            IconButton(onClick = { viewModel.enterSelection() }) {
                Icon(
                    Icons.Outlined.Checklist,
                    contentDescription = "多选",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = { showFilterSheet = true }) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = "筛选",
                    tint = if (filters.isActive) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            IconButton(onClick = { showMoreMenu = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
            }
            }
        },
    ) {
        if (display.showPrivacyNote) {
            item(key = "privacy-note") {
                Text(
                    text = "时间线仅记录应用、渠道、时间与数量，不保存通知标题、正文或附件。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "search") {
            OutlinedTextField(
                value = filters.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp, vertical = 4.dp),
                singleLine = true,
                placeholder = { Text("搜索应用 / 渠道") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (filters.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "清除")
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
            )
        }

        if (dayGroups.isNotEmpty() || summary.todayCount > 0) {
            item(key = "summary") {
                SummaryCard(summary)
            }
        }

        dayGroups.forEach { day ->
            item(key = "day-${day.dayLabel}") {
                SmallTitle(
                    text = "${day.dayLabel} · ${day.total} 条",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            day.apps.forEach { appGroup ->
                item(key = "app-${day.dayLabel}-${appGroup.packageName}") {
                    RecordAppCard(
                        group = appGroup,
                        display = display,
                        selectionMode = selection.mode,
                        selectedKeys = selection.selectedKeys,
                        onToggle = {
                            if (selection.mode) {
                                viewModel.toggleAppSelection(appGroup.packageName)
                            } else {
                                viewModel.toggleApp(appGroup.packageName)
                            }
                        },
                        onToggleExpand = { viewModel.toggleApp(appGroup.packageName) },
                        onLongPressApp = { viewModel.enterSelection(appGroup.packageName) },
                        onChannelClick = { item ->
                            if (selection.mode) {
                                viewModel.toggleItemSelection(item)
                            } else {
                                actionTarget = item
                            }
                        },
                    )
                }
            }
        }

        if (items.isNotEmpty() && filters.source != RecordSource.TIMELINE) {
            item(key = "mute-title") {
                SmallTitle(
                    text = "静音操作",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            items(items, key = { it.id }) { row ->
                RecordCard(row)
            }
        }

        if (items.isEmpty() && dayGroups.isEmpty()) {
            item {
                Text(
                    text = "暂无记录。开启通知使用权后，收到通知时会在这里生成弱采集时间线。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showFilterSheet) {
        RecordFilterSheet(
            filters = filters,
            onDismiss = { showFilterSheet = false },
            onSource = viewModel::setSource,
            onTimeRange = viewModel::setTimeRange,
            onSort = viewModel::setSort,
            onImportance = viewModel::setMinImportance,
            onSound = viewModel::setSoundFilter,
            onReset = viewModel::resetFilters,
        )
    }

    if (showMoreMenu) {
        HyperOsPopup(onDismissRequest = { showMoreMenu = false }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HyperOsPopupRow(
                    title = "显示选项",
                    selected = false,
                    showCheck = false,
                    onClick = {
                        showMoreMenu = false
                        showDisplaySheet = true
                    },
                )
                HyperOsPopupRow(
                    title = "清空全部",
                    selected = false,
                    showCheck = false,
                    onClick = {
                        showMoreMenu = false
                        viewModel.clearAll()
                    },
                )
                HyperOsPopupRow(
                    title = "仅清空时间线",
                    selected = false,
                    showCheck = false,
                    onClick = {
                        showMoreMenu = false
                        viewModel.clearTimelineOnly()
                    },
                )
                HyperOsPopupRow(
                    title = "仅清空静音日志",
                    selected = false,
                    showCheck = false,
                    onClick = {
                        showMoreMenu = false
                        viewModel.clearMuteLogOnly()
                    },
                )
            }
        }
    }

    if (showDisplaySheet) {
        RecordDisplaySheet(
            display = display,
            onDismiss = { showDisplaySheet = false },
            onToggle = viewModel::toggleDisplay,
            onReset = viewModel::resetDisplayPrefs,
        )
    }

    actionTarget?.let { target ->
        HyperOsPopup(onDismissRequest = { actionTarget = null }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HyperOsPopupRow("静音此渠道", selected = false, showCheck = false, onClick = {
                    viewModel.applyChannelAction(target, RuleAction.MUTE)
                    actionTarget = null
                })
                HyperOsPopupRow("降级此渠道", selected = false, showCheck = false, onClick = {
                    viewModel.applyChannelAction(target, RuleAction.DOWNGRADE)
                    actionTarget = null
                })
                HyperOsPopupRow("恢复默认", selected = false, showCheck = false, onClick = {
                    viewModel.applyChannelAction(target, RuleAction.KEEP)
                    actionTarget = null
                })
            }
        }
    }
}

@Composable
private fun RecordFilterSheet(
    filters: RecordFilters,
    onDismiss: () -> Unit,
    onSource: (RecordSource) -> Unit,
    onTimeRange: (RecordTimeRange) -> Unit,
    onSort: (RecordSort) -> Unit,
    onImportance: (Int) -> Unit,
    onSound: (Boolean, Boolean) -> Unit,
    onReset: () -> Unit,
) {
    HyperOsPopup(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HyperOsPopupRow("来源：全部", selected = filters.source == RecordSource.ALL, onClick = { onSource(RecordSource.ALL) })
            HyperOsPopupRow("来源：通知时间线", selected = filters.source == RecordSource.TIMELINE, onClick = { onSource(RecordSource.TIMELINE) })
            HyperOsPopupRow("来源：静音操作", selected = filters.source == RecordSource.MUTE_LOG, onClick = { onSource(RecordSource.MUTE_LOG) })
            HyperOsPopupDivider()
            HyperOsPopupRow("时间：全部", selected = filters.timeRange == RecordTimeRange.ALL, onClick = { onTimeRange(RecordTimeRange.ALL) })
            HyperOsPopupRow("时间：今天", selected = filters.timeRange == RecordTimeRange.TODAY, onClick = { onTimeRange(RecordTimeRange.TODAY) })
            HyperOsPopupRow("时间：近 7 天", selected = filters.timeRange == RecordTimeRange.LAST_7_DAYS, onClick = { onTimeRange(RecordTimeRange.LAST_7_DAYS) })
            HyperOsPopupDivider()
            HyperOsPopupRow("排序：时间↓", selected = filters.sort == RecordSort.TIME_DESC, onClick = { onSort(RecordSort.TIME_DESC) })
            HyperOsPopupRow("排序：次数↓", selected = filters.sort == RecordSort.COUNT_DESC, onClick = { onSort(RecordSort.COUNT_DESC) })
            HyperOsPopupRow("排序：应用名", selected = filters.sort == RecordSort.APP_NAME, onClick = { onSort(RecordSort.APP_NAME) })
            HyperOsPopupDivider()
            HyperOsPopupRow("importance：全部", selected = filters.minImportance < 0, onClick = { onImportance(-1) })
            HyperOsPopupRow("importance：HIGH", selected = filters.minImportance == 4, onClick = { onImportance(4) })
            HyperOsPopupRow("importance：DEFAULT", selected = filters.minImportance == 3, onClick = { onImportance(3) })
            HyperOsPopupRow("importance：LOW", selected = filters.minImportance == 2, onClick = { onImportance(2) })
            HyperOsPopupDivider()
            HyperOsPopupRow("声音：全部", selected = !filters.soundOnOnly && !filters.soundOffOnly, onClick = { onSound(false, false) })
            HyperOsPopupRow("声音：开", selected = filters.soundOnOnly, onClick = { onSound(true, false) })
            HyperOsPopupRow("声音：关", selected = filters.soundOffOnly, onClick = { onSound(false, true) })
            HyperOsPopupDivider()
            HyperOsPopupRow("重置筛选", selected = false, showCheck = false, onClick = onReset)
        }
    }
}

@Composable
private fun RecordDisplaySheet(
    display: RecordDisplayPrefs,
    onDismiss: () -> Unit,
    onToggle: (String) -> Unit,
    onReset: () -> Unit,
) {
    HyperOsPopup(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HyperOsPopupRow("应用图标", selected = display.showAppIcon, onClick = { onToggle("appIcon") })
            HyperOsPopupRow("应用名", selected = display.showAppName, onClick = { onToggle("appName") })
            HyperOsPopupRow("包名", selected = display.showPackageName, onClick = { onToggle("packageName") })
            HyperOsPopupRow("渠道名", selected = display.showChannelName, onClick = { onToggle("channelName") })
            HyperOsPopupRow("渠道 ID", selected = display.showChannelId, onClick = { onToggle("channelId") })
            HyperOsPopupRow("importance", selected = display.showImportance, onClick = { onToggle("importance") })
            HyperOsPopupRow("声音点", selected = display.showSoundDot, onClick = { onToggle("soundDot") })
            HyperOsPopupRow("振动", selected = display.showVibration, onClick = { onToggle("vibration") })
            HyperOsPopupRow("时间范围", selected = display.showTimeRange, onClick = { onToggle("timeRange") })
            HyperOsPopupRow("次数", selected = display.showCount, onClick = { onToggle("count") })
            HyperOsPopupRow("隐私说明", selected = display.showPrivacyNote, onClick = { onToggle("privacyNote") })
            HyperOsPopupDivider()
            HyperOsPopupRow("恢复默认显示", selected = false, showCheck = false, onClick = onReset)
        }
    }
}

@Composable
private fun RecordAppCard(
    group: TimelineAppGroup,
    display: RecordDisplayPrefs,
    onToggle: () -> Unit,
    onChannelClick: (TimelineItem) -> Unit,
    selectionMode: Boolean = false,
    selectedKeys: Set<String> = emptySet(),
    onToggleExpand: () -> Unit = onToggle,
    onLongPressApp: () -> Unit = {},
) {
    val selectedCount = group.channels.count {
        (it.packageName + "|" + it.channelId) in selectedKeys
    }
    val total = group.channels.size
    val header: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (selectionMode) {
                RecordSelectionCheck(
                    selected = total > 0 && selectedCount == total,
                    partial = selectedCount > 0 && selectedCount < total,
                )
            } else if (display.showAppIcon) {
                AppIcon(packageName = group.packageName)
            }
            Column(modifier = Modifier.weight(1f)) {
                if (display.showAppName) {
                    MiuixText(group.appLabel, style = MaterialTheme.typography.titleLarge)
                }
                val meta = buildString {
                    if (display.showPackageName) append(group.packageName)
                    if (isNotEmpty()) append(" · ")
                    append(group.channels.sumOf { it.count })
                    append(" 条")
                    if (selectionMode && selectedCount > 0) {
                        append(" · 已选 $selectedCount")
                    }
                }
                MiuixText(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToggleExpand) {
                Icon(
                    imageVector = if (group.expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (group.expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (group.expanded) {
        // Expanded: static surface — no whole-card tilt while reading channels.
        Card(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggle),
                ) {
                    header()
                }
                group.channels.forEach { row ->
                    TimelineRow(
                        item = row,
                        display = display,
                        selectionMode = selectionMode,
                        selected = (row.packageName + "|" + row.channelId) in selectedKeys,
                        onClick = { onChannelClick(row) },
                    )
                }
            }
        }
    } else {
        // Collapsed: same InstallerX press feedback as home app cards.
        PressableCard(
            onClick = onToggle,
            onLongClick = onLongPressApp,
            cornerRadius = 20.dp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                header()
            }
        }
    }
}

@Composable
private fun RecordSelectionCheck(selected: Boolean, partial: Boolean = false) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(
                when {
                    selected -> Color(0xFF3482FF)
                    partial -> Color(0xFF3482FF).copy(alpha = 0.35f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
            partial -> Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawable.toBitmap(96, 96).asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = packageName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TimelineRow(
    item: TimelineItem,
    display: RecordDisplayPrefs,
    onClick: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selectionMode) {
                RecordSelectionCheck(selected = selected)
            }
            if (display.showChannelName) {
                Text(
                    text = item.channelName,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = item.channelId,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (display.showCount) {
                Text(
                    text = "${item.count} 次",
                    style = MaterialTheme.typography.labelMedium,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
        val parts = buildList {
            if (display.showChannelId && display.showChannelName) add(item.channelId)
            if (display.showImportance) add(item.importanceLabel)
            if (display.showVibration) {
                add(
                    when (item.vibrationEnabled) {
                        true -> "有振动"
                        false -> "无振动"
                        null -> ""
                    },
                )
            }
            if (display.showTimeRange) add(item.timeRange)
        }.filter { it.isNotBlank() }
        if (parts.isNotEmpty()) {
            Text(
                text = parts.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (display.showSoundDot && item.soundEnabled != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (item.soundEnabled) Color(0xFF8E8E93) else Color(0xFFFF3B30),
                            shape = CircleShape,
                        ),
                )
                Text(
                    text = if (item.soundEnabled) "声音开" else "声音关",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: TimelineSummary) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("今日 ${summary.todayCount} 条", style = MaterialTheme.typography.titleLarge)
            if (summary.topApps.isNotEmpty()) {
                Text("Top 应用", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                summary.topApps.forEach { (label, count) ->
                    Text(
                        text = "$label · $count",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (summary.topChannels.isNotEmpty()) {
                Text("Top 渠道", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                summary.topChannels.forEach { (label, count) ->
                    Text(
                        text = "$label · $count",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordCard(item: RecordItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TagPill(item.tag)
            }
            Text(
                item.subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.time,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TagPill(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = Color(0xFF3482FF),
    )
}
