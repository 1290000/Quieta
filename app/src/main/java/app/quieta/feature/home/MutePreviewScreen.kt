package app.quieta.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.quieta.R
import app.quieta.core.model.RuleAction
import app.quieta.ui.component.PressableCard
import app.quieta.ui.component.QuietaPage
import app.quieta.ui.theme.QuietaTextStyles
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MutePreviewScreen(
    preview: MutePreview?,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    onScopeChange: (MuteScope) -> Unit,
    modifier: Modifier = Modifier,
    blurEnabled: Boolean = true,
) {
    BackHandler(onBack = onBack)
    QuietaPage(
        title = "静音预览",
        modifier = modifier,
        blurEnabled = blurEnabled,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
            }
        },
    ) {
        if (preview == null) {
            item(key = "empty") {
                Text(
                    text = "没有待确认的静音计划。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            return@QuietaPage
        }

        item(key = "tip") {
            // Same blue notice as InstallerX / privilege page.
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.primary.copy(alpha = 0.2f)),
            ) {
                Text(
                    text = "将静音 ${preview.muteCount} 个渠道，降级 ${preview.downgradeCount} 个渠道。确认后仅写入下列目标。",
                    modifier = Modifier.padding(16.dp),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
            }
        }

        if (preview.filterActive) {
            item(key = "scope") {
                SmallTitle(
                    text = "写入范围",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    ScopeRow(
                        title = "全部命中",
                        subtitle = "当前盘点中所有规则命中的渠道",
                        selected = preview.scope == MuteScope.ALL,
                        onClick = { onScopeChange(MuteScope.ALL) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MiuixTheme.colorScheme.dividerLine,
                    )
                    ScopeRow(
                        title = "仅当前筛选",
                        subtitle = "只写入主页搜索/筛选结果",
                        selected = preview.scope == MuteScope.FILTERED,
                        onClick = { onScopeChange(MuteScope.FILTERED) },
                    )
                }
            }
        }

        item(key = "actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(text = "取消", onClick = onBack)
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    enabled = preview.items.isNotEmpty(),
                ) {
                    Text("确认静音")
                }
            }
        }

        item(key = "list-title") {
            SmallTitle(
                text = "目标渠道 ${preview.items.size}",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        if (preview.items.isEmpty()) {
            item(key = "no-hit") {
                Text(
                    text = "没有可执行的规则命中渠道。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        } else {
            // Group by app for InstallerX-like list density.
            val grouped = preview.items.groupBy { it.appLabel to it.packageName }
            grouped.forEach { (key, list) ->
                item(key = "g-" + key.second) {
                    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(key.first, style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = key.second + " · " + list.size + " 个渠道",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            list.forEachIndexed { index, item ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        thickness = 0.5.dp,
                                        color = MiuixTheme.colorScheme.dividerLine,
                                    )
                                }
                                PreviewChannelRow(item)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    PressableCard(
        onClick = onClick,
        cornerRadius = 0.dp,
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (selected) "✓" else "",
                style = QuietaTextStyles.statusDetail,
                color = MiuixTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PreviewChannelRow(item: MutePreviewItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.channelName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = item.channelId + " · " +
                    when (item.action) {
                        RuleAction.MUTE -> "静音"
                        RuleAction.DOWNGRADE -> "降级"
                        RuleAction.KEEP -> "保留"
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = item.reason,
                style = MaterialTheme.typography.labelSmall,
                color = MiuixTheme.colorScheme.primary,
            )
        }
    }
}
