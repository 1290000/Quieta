package app.quieta.feature.home

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quieta.R
import app.quieta.core.model.AppChannels
import app.quieta.core.model.Channel
import app.quieta.core.model.RuleAction
import app.quieta.ui.component.QuietaPage
import app.quieta.ui.component.PressableCard
import rikka.shizuku.Shizuku

private const val REQ_SHIZUKU = 1001

// InstallerX-style status colors (light theme primary)
private val StatusGreenBg = Color(0xFFDFFAE4)
private val StatusGreenIcon = Color(0xFF34C759)
private val StatusRedBg = Color(0xFFFAEEEE)
private val StatusRedIcon = Color(0xFFFF3B30)
private val StatusNeutralBg = Color(0xFFF0F0F1)
private val StatusNeutralIcon = Color(0xFF8E8E93)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
    onOpenPrivilege: () -> Unit = {},
    onOpenConfig: () -> Unit = {},
    blurEnabled: Boolean = true,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    viewModel.refresh()
                }
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        onDispose { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    QuietaPage(
        title = stringResource(R.string.home_title),
        modifier = modifier,
        blurEnabled = blurEnabled,
    ) {
        item {
            StatusGrid(
                gate = state.gate,
                privilege = state.privilege,
                authorizerCount = listOf(state.rootAvailable, state.shizukuAuthorized, state.dhizukuAvailable).count { it },
                rulesCount = state.rulesCount,
                onOpenPrivilege = onOpenPrivilege,
                onOpenConfig = onOpenConfig,
            )
        }

        item {
            GateActions(
                state = state,
                onRequestPermission = {
                    runCatching { Shizuku.requestPermission(REQ_SHIZUKU) }
                },
                onOpenPrivilege = onOpenPrivilege,
                onRefresh = viewModel::refresh,
                onApplyMute = viewModel::applyBatchMute,
            )
        }

        item {
            DeviceInfoCard(privilegeLabel = state.privilege.label, autoMuteOn = state.autoMuteOn)
        }

        state.error?.let { message ->
            item {
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }

        state.muteResult?.let { result ->
            item {
                Text(
                    text = "批量静音：成功 ${result.success} / ${result.total}，失败 ${result.failed}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (state.gate == PrivilegeGate.READY && state.apps.isNotEmpty()) {
            item {
                Text(
                    text = "通知渠道",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(state.apps, key = { it.packageName }) { app ->
                AppChannelCard(app = app, plan = state.plan)
            }
        }

        if (!state.loading && state.apps.isEmpty() && state.gate == PrivilegeGate.READY) {
            item {
                Text(
                    text = "未发现带通知渠道的应用（或读取失败）。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** InstallerX-like: big status card + two stat cards. */
@Composable
private fun StatusGrid(
    gate: PrivilegeGate,
    privilege: app.quieta.core.model.PrivilegeStatus,
    authorizerCount: Int,
    rulesCount: Int,
    onOpenPrivilege: () -> Unit,
    onOpenConfig: () -> Unit,
) {
    // Neutral while probing so the card does not flash red before Shizuku is known.
    val checking = gate == PrivilegeGate.CHECKING
    val active = privilege.available && !checking
    val containerColor = if (isSystemInDarkTheme()) {
        when {
            active -> app.quieta.ui.theme.QuietaColors.StatusGreenDark
            checking -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.errorContainer
        }
    } else {
        when {
            active -> StatusGreenBg
            checking -> StatusNeutralBg
            else -> StatusRedBg
        }
    }
    val iconTint = when {
        active -> StatusGreenIcon
        checking -> StatusNeutralIcon
        else -> StatusRedIcon
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PressableCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenPrivilege,
            cornerRadius = 20.dp,
            color = containerColor,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Large decorative icon at bottom-right (InstallerX pattern)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(x = 40.dp, y = 28.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Icon(
                        imageVector = when {
                            active -> Icons.Rounded.CheckCircleOutline
                            checking -> Icons.Outlined.Refresh
                            else -> Icons.Rounded.ErrorOutline
                        },
                        contentDescription = null,
                        tint = iconTint.copy(alpha = 0.85f),
                        modifier = Modifier.size(150.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        text = when {
                            active -> "正在作为通知降噪工具工作"
                            checking -> "正在检测通知降噪能力"
                            else -> "通知降噪未就绪"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            active -> "已通过 ${privilege.label} 读取通知渠道"
                            checking -> "检测中…"
                            else -> privilege.label
                        },
                        style = app.quieta.ui.theme.QuietaTextStyles.statusDetail,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    )
                    Spacer(modifier = Modifier.height(36.dp))
                    Text(
                        text = if (checking) "…" else privilege.label,
                        style = app.quieta.ui.theme.QuietaTextStyles.statusDetail,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                title = stringResource(R.string.home_stat_authorizers),
                value = authorizerCount.toString(),
                onClick = onOpenPrivilege,
            )
            StatCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                title = "规则数量",
                value = rulesCount.toString(),
                onClick = onOpenConfig,
            )
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PressableCard(
        modifier = modifier,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = app.quieta.ui.theme.QuietaTextStyles.statLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun GateActions(
    state: HomeUiState,
    onRequestPermission: () -> Unit,
    onOpenPrivilege: () -> Unit,
    onRefresh: () -> Unit,
    onApplyMute: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state.gate) {
                PrivilegeGate.CHECKING -> Text(
                    text = "检测中…",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                PrivilegeGate.NEED_PERMISSION -> {
                    Button(onClick = onRequestPermission, modifier = Modifier.weight(1f)) {
                        Text("请求 Shizuku 授权")
                    }
                }
                PrivilegeGate.SHIZUKU_UNAVAILABLE -> {
                    OutlinedButton(onClick = onOpenPrivilege, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_stat_authorizers))
                    }
                    TextButton(onClick = onRefresh) { Text("重试") }
                }
                PrivilegeGate.READY -> {
                    OutlinedButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("刷新")
                    }
                    Button(
                        onClick = onApplyMute,
                        enabled = state.plan.any { it.value != RuleAction.KEEP } &&
                            (state.privilege.id != app.quieta.core.model.PrivilegeId.ROOT || state.rootWriteSupported),
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        ),
                    ) {
                        Icon(Icons.Outlined.VolumeOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("按规则静音")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoCard(privilegeLabel: String, autoMuteOn: Boolean) {
    val deviceName = androidx.compose.runtime.remember { app.quieta.core.device.DeviceNames.display() }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            InfoBlock("机型", deviceName)
            InfoBlock("系统", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
            InfoBlock("正在使用的特权", privilegeLabel)
            InfoBlock("自动静音", if (autoMuteOn) "已开启" else "关闭")
        }
    }
}

@Composable
private fun InfoBlock(title: String, value: String) {
    // InstallerX BasicComponent: title 18sp SemiBold + summary 14sp gray
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppChannelCard(app: AppChannels, plan: Map<Channel, RuleAction>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(app.appLabel, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = app.packageName + " · " + app.channels.size + " 个渠道",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            app.channels.forEach { channel ->
                ChannelRow(channel = channel, action = plan[channel] ?: RuleAction.KEEP)
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: Channel, action: RuleAction) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(channel.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = channel.id,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ActionChip(action)
    }
}

@Composable
private fun ActionChip(action: RuleAction) {
    val label = when (action) {
        RuleAction.KEEP -> "保留"
        RuleAction.MUTE -> "静音"
        RuleAction.DOWNGRADE -> "降级"
    }
    val container = when (action) {
        RuleAction.KEEP -> MaterialTheme.colorScheme.surfaceVariant
        RuleAction.MUTE -> Color(0xFFFFE5E1)
        RuleAction.DOWNGRADE -> Color(0xFFFFF1CC)
    }
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
