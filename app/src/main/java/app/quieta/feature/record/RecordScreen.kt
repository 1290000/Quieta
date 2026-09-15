package app.quieta.feature.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quieta.R
import app.quieta.ui.component.QuietaPage

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
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()

    QuietaPage(
        title = stringResource(R.string.record_title),
        modifier = modifier,
        blurEnabled = blurEnabled,
        actions = {
            IconButton(onClick = viewModel::clearAll) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "清空")
            }
            IconButton(onClick = { /* 记录筛选属于后续清单项 */ }) {
                Icon(Icons.Outlined.Tune, contentDescription = "筛选")
            }
        },
    ) {
        item(key = "privacy-note") {
            Text(
                text = "时间线仅记录应用包名、通知渠道、时间和数量，不保存通知标题、正文或附件。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (timeline.isNotEmpty()) {
            item(key = "timeline-title") {
                SectionLabel("通知时间线")
            }
            items(timeline, key = { it.id }) { row ->
                TimelineCard(row)
            }
        }
        if (items.isNotEmpty()) {
            item(key = "mute-title") {
                SectionLabel("静音操作")
            }
            items(items, key = { it.id }) { row ->
                RecordCard(row)
            }
        }
        if (items.isEmpty() && timeline.isEmpty()) {
            item {
                Text(
                    text = "暂无记录。开启通知使用权后，收到通知时会在这里生成弱采集时间线。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun TimelineCard(item: TimelineItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.packageName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    text = "${item.count} 次",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "渠道：${item.channelId}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                text = item.time,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            Text(item.subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(item.time, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TagPill(text: String) {
    Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
}
