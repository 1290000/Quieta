package app.quieta.feature.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quieta.BuildConfig
import app.quieta.R
import app.quieta.service.NotificationListenerAccess
import app.quieta.ui.component.QuietaPage
import app.quieta.ui.component.QuietaSwitch
import app.quieta.ui.glass.FloatingBottomBarMode
import java.text.DateFormat
import java.util.Date

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
    onOpenChannelImport: () -> Unit = {},
) {
    val context = LocalContext.current
    val autoMute by viewModel.autoMuteNewChannels.collectAsStateWithLifecycle()
    val timelineEnabled by viewModel.notificationTimelineEnabled.collectAsStateWithLifecycle()
    val healthCheck by viewModel.timelineHealthCheckEnabled.collectAsStateWithLifecycle()
    val keepAlive by viewModel.timelineKeepAliveEnabled.collectAsStateWithLifecycle()
    val diagnostics by viewModel.timelineDiagnostics.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshTimelineDiagnostics()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                    title = stringResource(R.string.theme_settings),
                    subtitle = stringResource(R.string.theme_settings_theme_mode) + " · " + stringResource(R.string.theme_settings_use_blur),
                    onClick = onOpenTheme,
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
                    onCheckedChange = { enabled ->
                        viewModel.setNotificationTimelineEnabled(enabled)
                        if (enabled) {
                            NotificationListenerAccess.ensureBound(context, "timeline_enabled")
                        }
                    },
                )
                NavRow(
                    title = "通知使用权",
                    subtitle = if (diagnostics.listenerEnabled) {
                        "已授权"
                    } else {
                        "打开系统设置，允许息匣读取通知"
                    },
                    onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                        NotificationListenerAccess.ensureBound(context, "open_listener_settings")
                    },
                )
            }
        }
        item(key = "timeline_recovery") {
            SectionTitle("时间线后台采集")
            SettingsGroup {
                TimelineDiagnosticsCard(
                    diagnostics = diagnostics,
                    onRebind = { viewModel.rebindListener() },
                )
                SwitchRow(
                    title = "低频监听健康检查",
                    subtitle = "默认关。约 20 分钟检查一次绑定，无前台通知；划掉后台后更易恢复采集",
                    checked = healthCheck,
                    onCheckedChange = { viewModel.setTimelineHealthCheckEnabled(it) },
                )
                SwitchRow(
                    title = "后台持续采集",
                    subtitle = "默认关。会显示低优先级通知并常驻监听进程，更耗电；适合必须不间断记录时",
                    checked = keepAlive,
                    onCheckedChange = { viewModel.setTimelineKeepAliveEnabled(it) },
                )
                NavRow(
                    title = "系统后台与锁定",
                    subtitle = "在最近任务锁定息匣；电池设为无限制；允许自启动（如系统提供）",
                    onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            })
                        }
                    },
                )
            }
        }
        item(key = "backup") {
            SectionTitle("备份与还原")
            SettingsGroup {
                NavRow(
                    title = "导出 / 导入规则",
                    subtitle = "配置页：勾选导出 JSON；导入支持合并或替换",
                    onClick = { /* config tab owns the real entry */ },
                )
                NavRow(
                    title = "导出渠道设置",
                    subtitle = "按当前盘点导出 importance 等 JSON（主页可按选中渠道导出）",
                    onClick = { viewModel.exportChannelSnapshot() },
                )
                NavRow(
                    title = "导入渠道设置",
                    subtitle = "预览快照差异，确认后写入 importance 并回读校验",
                    onClick = onOpenChannelImport,
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
private fun TimelineDiagnosticsCard(
    diagnostics: TimelineDiagnosticsUiState,
    onRebind: () -> Unit,
) {
    val accessText = if (diagnostics.listenerEnabled) "已授权" else "未授权"
    val connectText = when {
        diagnostics.listenerConnected -> "已连接"
        diagnostics.listenerEnabled -> "未连接（可能已被最近任务结束，可点重新绑定）"
        else -> "未连接"
    }
    val lastText = if (diagnostics.lastEventAt > 0L) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(diagnostics.lastEventAt))
    } else {
        "尚无数据"
    }
    val healthText = if (diagnostics.healthCheckEnabled) {
        if (diagnostics.healthJobScheduled) "已开启" else "已开启（等待系统调度）"
    } else {
        "关闭"
    }
    val keepAliveText = when {
        diagnostics.keepAliveEnabled && diagnostics.keepAliveRunning -> "开启 · 监听进程运行中"
        diagnostics.keepAliveEnabled && !diagnostics.notificationsEnabled -> "开启 · 系统通知关闭，前台通知可能不显示"
        diagnostics.keepAliveEnabled -> "开启 · 等待系统拉起前台服务"
        else -> "关闭"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("采集状态", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(6.dp))
        DiagnosticsLine("通知使用权", accessText)
        DiagnosticsLine("监听连接", connectText)
        DiagnosticsLine("上次采集", lastText)
        DiagnosticsLine("健康检查", healthText)
        DiagnosticsLine("持续采集", keepAliveText)
        if (diagnostics.keepAliveEnabled) {
            Text(
                text = "建议：最近任务锁定息匣 · 电池无限制 · 允许自启动。系统仍可能结束后台，掉线时请点重新绑定。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "重新绑定",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onRebind)
                .padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun DiagnosticsLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
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
