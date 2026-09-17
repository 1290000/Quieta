package app.quieta.feature.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quieta.R
import app.quieta.core.model.RuleAction
import app.quieta.ui.component.HyperOsPopup
import app.quieta.ui.component.HyperOsPopupRow
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
    var actionTarget by remember { mutableStateOf<TimelineItem?>(null) }

    QuietaPage(
        title = stringResource(R.string.record_title),
        modifier = modifier,
        blurEnabled = blurEnabled,
        actions = {
            IconButton(onClick = viewModel::clearAll) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "清空")
            }
            IconButton(onClick = { /* filter later */ }) {
                Icon(Icons.Outlined.Tune, contentDescription = "筛选")
            }
        },
    ) {
        item(key = "privacy-note") {
            Text(
                text = "时间线仅记录应用、渠道、时间与数量，不保存通知标题、正文或附件。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Card(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleApp(appGroup.packageName) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    MiuixText(appGroup.appLabel, style = MaterialTheme.typography.titleLarge)
                                    MiuixText(
                                        text = appGroup.packageName + " · " +
                                            appGroup.channels.sumOf { it.count } + " 条",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (appGroup.expanded) {
                                appGroup.channels.forEach { row ->
                                    TimelineRow(
                                        item = row,
                                        onClick = { actionTarget = row },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (items.isNotEmpty()) {
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

    actionTarget?.let { target ->
        HyperOsPopup(onDismissRequest = { actionTarget = null }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HyperOsPopupRow(
                    title = "静音此渠道",
                    selected = false,
                    showCheck = false,
                    onClick = {
                        viewModel.applyChannelAction(target, RuleAction.MUTE)
                        actionTarget = null
                    },
                )
                HyperOsPopupRow(
                    title = "降级此渠道",
                    selected = false,
                    showCheck = false,
                    onClick = {
                        viewModel.applyChannelAction(target, RuleAction.DOWNGRADE)
                        actionTarget = null
                    },
                )
                HyperOsPopupRow(
                    title = "恢复默认",
                    selected = false,
                    showCheck = false,
                    onClick = {
                        viewModel.applyChannelAction(target, RuleAction.KEEP)
                        actionTarget = null
                    },
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
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
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
private fun TimelineRow(item: TimelineItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.channelName,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${item.count} 次",
                style = MaterialTheme.typography.labelMedium,
                color = MiuixTheme.colorScheme.primary,
            )
        }
        Text(
            text = item.channelId + " · " + item.importanceLabel + " · " + item.timeRange,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecordCard(item: RecordItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
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
