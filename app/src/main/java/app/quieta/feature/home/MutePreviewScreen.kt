package app.quieta.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.quieta.R
import app.quieta.core.model.RuleAction
import app.quieta.ui.component.QuietaPage
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
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
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(16.dp),
                )
            }
            return@QuietaPage
        }

        item(key = "summary") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "将静音 ${preview.muteCount} 个渠道，降级 ${preview.downgradeCount} 个渠道。",
                    style = MiuixTheme.textStyles.body1,
                )
                if (preview.filterActive) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val allSelected = preview.scope == MuteScope.ALL
                        Button(onClick = { onScopeChange(MuteScope.ALL) }) {
                            Text(if (allSelected) "全部命中 ✓" else "全部命中")
                        }
                        Button(onClick = { onScopeChange(MuteScope.FILTERED) }) {
                            Text(if (!allSelected) "仅当前筛选 ✓" else "仅当前筛选")
                        }
                    }
                    Text(
                        text = if (preview.scope == MuteScope.FILTERED) {
                            "只写入当前搜索/筛选结果。"
                        } else {
                            "写入当前盘点中全部规则命中渠道。"
                        },
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = "取消",
                        onClick = onBack,
                    )
                    Button(
                        onClick = onConfirm,
                        enabled = preview.items.isNotEmpty(),
                    ) {
                        Text("确认静音")
                    }
                }
            }
        }

        if (preview.items.isEmpty()) {
            item(key = "no-hit") {
                Text(
                    text = "没有可执行的规则命中渠道。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        } else {
            items(preview.items, key = { it.packageName + "|" + it.channelId }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.appLabel + " · " + item.channelName, style = MiuixTheme.textStyles.body1)
                        Text(
                            text = item.channelId + " → " +
                                when (item.action) {
                                    RuleAction.MUTE -> "静音"
                                    RuleAction.DOWNGRADE -> "降级"
                                    RuleAction.KEEP -> "保留"
                                },
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Text(
                            text = item.reason,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
