package app.quieta.feature.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quieta.BuildConfig
import app.quieta.R
import app.quieta.ui.component.QuietaPage
import app.quieta.ui.component.QuietaSwitch
import app.quieta.ui.glass.FloatingBottomBarMode

@Composable
fun SettingsScreen(
    blurEnabled: Boolean,
    onBlurEnabledChange: (Boolean) -> Unit,
    bottomBarMode: FloatingBottomBarMode,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
    aboutViewModel: AboutViewModel = viewModel(),
    onOpenTheme: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
) {
    val context = LocalContext.current
    val autoMute by viewModel.autoMuteNewChannels.collectAsStateWithLifecycle()
    val timelineEnabled by viewModel.notificationTimelineEnabled.collectAsStateWithLifecycle()

    QuietaPage(
        title = stringResource(R.string.settings_title),
        modifier = modifier,
        blurEnabled = blurEnabled,
        itemSpacing = 0.dp,
    ) {
        item(key = "appearance") {
            SectionTitle("个性化")
            SettingsGroup {
                NavRow(
                    title = "主题设置",
                    subtitle = "更改应用主题",
                    onClick = onOpenTheme,
                )
                NavRow(
                    title = "液态玻璃",
                    subtitle = modeLabel(bottomBarMode) + " · 开关",
                    onClick = { onBlurEnabledChange(!blurEnabled) },
                    trailing = {
                        QuietaSwitch(checked = blurEnabled, onCheckedChange = onBlurEnabledChange)
                    },
                )
            }
        }
        item(key = "general") {
            SectionTitle("常规")
            SettingsGroup {
                SwitchRow(
                    title = "新渠道自动静音",
                    subtitle = stringResource(app.quieta.R.string.auto_mute_requirement),
                    checked = autoMute,
                    onCheckedChange = { viewModel.setAutoMuteNewChannels(it) },
                )
                SwitchRow(
                    title = "通知时间线",
                    subtitle = "仅记录包名、渠道、时间和数量，不保存通知内容",
                    checked = timelineEnabled,
                    onCheckedChange = { viewModel.setNotificationTimelineEnabled(it) },
                )
                NavRow(
                    title = "通知使用权",
                    subtitle = "打开系统设置，允许息匣读取通知",
                    onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    },
                )
            }
        }
        item(key = "backup") {
            SectionTitle("备份与还原")
            SettingsGroup {
                NavRow(
                    title = "导出规则",
                    subtitle = "在配置页分享 JSON 备份",
                    onClick = { /* config tab */ },
                )
            }
        }
        // InstallerX: settings only shows a single About entry; actions live on the secondary page.
        item(key = "about") {
            SectionTitle("其它")
            SettingsGroup {
                NavRow(
                    title = "关于 息匣",
                    subtitle = BuildConfig.VERSION_NAME,
                    onClick = onOpenAbout,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = Color_Section,
        modifier = Modifier.padding(bottom = 8.dp, top = 4.dp),
    )
}

private val Color_Section = androidx.compose.ui.graphics.Color(0xFF8E8E93)

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column { content() }
    }
}

@Composable
private fun NavRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        QuietaSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun modeLabel(mode: FloatingBottomBarMode): String = when (mode) {
    FloatingBottomBarMode.LiquidGlass -> "液态玻璃"
    FloatingBottomBarMode.Blur -> "毛玻璃"
    FloatingBottomBarMode.None -> "无模糊"
}
