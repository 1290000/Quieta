package app.quieta.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.quieta.R
import app.quieta.core.engine.QuietMode
import app.quieta.core.engine.SilentButAllowed
import app.quieta.core.model.AppChannels
import app.quieta.core.model.Channel
import app.quieta.core.model.RuleAction
import app.quieta.ui.component.HyperOsPopup
import app.quieta.ui.component.HyperOsPopupRow
import app.quieta.ui.component.QuietaPage
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Channel-level list: allow-notifications still on, but sound/peek/vibrate mostly off
 * (HyperOS channel page with only 允许通知 enabled).
 */
@Composable
fun SilentChannelsScreen(
    apps: List<AppChannels>,
    plan: Map<Channel, RuleAction>,
    onBack: () -> Unit,
    onChannelAction: (Channel, RuleAction) -> Unit,
    modifier: Modifier = Modifier,
    blurEnabled: Boolean = true,
    mode: QuietMode = QuietMode.SILENT_NO_SOUND,
) {
    // Root QuietaRoot PredictiveBackHandler owns back; do not intercept.
    BackHandler(enabled = false, onBack = onBack)
    val title = when (mode) {
        QuietMode.SILENT_NO_SOUND -> "静默仍开"
        QuietMode.QUIET_WITH_SOUND -> "仅声音·无横幅"
    }
    val tip = when (mode) {
        QuietMode.SILENT_NO_SOUND -> "允许通知仍开着，但声音/悬浮/振动基本已关闭的渠道。"
        QuietMode.QUIET_WITH_SOUND -> "允许通知仍开着，有声音、无悬浮横幅的渠道。"
    }
    val groups = apps.mapNotNull { app ->
        val hits = app.channels.filter { SilentButAllowed.isMatch(it, mode) }
        if (hits.isEmpty()) null else Triple(app.appLabel, app.packageName, hits)
    }
    val total = groups.sumOf { it.third.size }

    QuietaPage(
        title = title,
        modifier = modifier,
        blurEnabled = blurEnabled,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.navigate_back),
                )
            }
        },
    ) {
        item(key = "tip") {
            Text(
                text = tip + "（共 $total）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (groups.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "没有符合条件的渠道。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            groups.forEach { (label, pkg, channels) ->
                item(key = "h-$pkg") {
                    SmallTitle(
                        text = "$label · ${channels.size}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                item(key = "c-$pkg") {
                    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            channels.forEachIndexed { index, channel ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        thickness = 0.5.dp,
                                        color = MiuixTheme.colorScheme.dividerLine,
                                    )
                                }
                                SilentChannelRow(
                                    channel = channel,
                                    planned = plan[channel] ?: RuleAction.KEEP,
                                    onAction = { onChannelAction(channel, it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SilentChannelRow(
    channel: Channel,
    planned: RuleAction,
    onAction: (RuleAction) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showSheet = true }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(channel.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = buildString {
                    append(channel.id)
                    append(" · ")
                    append(channel.importance.name)
                    if (!channel.effectiveSoundEnabled) append(" · 声音关")
                    if (!channel.vibrationEnabled) append(" · 无振动")
                    if (channel.lockscreenHidden) append(" · 锁屏不显示")
                    if (planned != RuleAction.KEEP) append(" · 规则将处理")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = if (channel.effectiveSoundEnabled) Color(0xFF8E8E93) else Color(0xFFFF3B30),
                        shape = CircleShape,
                    ),
            )
        }
    }
    if (showSheet) {
        HyperOsPopup(onDismissRequest = { showSheet = false }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HyperOsPopupRow(
                    title = "静音",
                    selected = false,
                    showCheck = false,
                    onClick = {
                        onAction(RuleAction.MUTE)
                        showSheet = false
                    },
                )
                HyperOsPopupRow(
                    title = "降级",
                    selected = false,
                    showCheck = false,
                    onClick = {
                        onAction(RuleAction.DOWNGRADE)
                        showSheet = false
                    },
                )
                HyperOsPopupRow(
                    title = "恢复",
                    selected = false,
                    showCheck = false,
                    onClick = {
                        onAction(RuleAction.KEEP)
                        showSheet = false
                    },
                )
            }
        }
    }
}
