package app.quieta.feature.home

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VolumeOff
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quieta.R
import app.quieta.core.model.AppChannels
import app.quieta.core.model.Channel
import app.quieta.core.model.RuleAction
import rikka.shizuku.Shizuku

private const val REQ_SHIZUKU = 1001
private val StatusGreenBg = Color(0xFFC8F0D8)
private val StatusGreenText = Color(0xFF14532D)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
        }

        item {
            StatusBanner(state = state)
        }

        item {
            GateActions(
                state = state,
                onRequestPermission = {
                    runCatching { Shizuku.requestPermission(REQ_SHIZUKU) }
                },
                onOpenShizuku = {
                    runCatching {
                        context.packageManager
                            .getLaunchIntentForPackage("moe.shizuku.privileged.api")
                            ?.let { context.startActivity(it) }
                    }
                },
                onRefresh = viewModel::refresh,
                onApplyMute = viewModel::applyBatchMute,
            )
        }

        item {
            StatRow(
                leftLabel = "可用特权",
                leftValue = if (state.privilege.available) "1" else "0",
                rightLabel = "规则数量",
                rightValue = state.rulesCount.toString(),
            )
        }

        item {
            DeviceInfoCard(state = state)
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
                    modifier = Modifier.padding(top = 8.dp),
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

@Composable
private fun StatusBanner(state: HomeUiState) {
    val ready = state.gate == PrivilegeGate.READY
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (ready) StatusGreenBg else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (ready) "正在作为通知降噪工具工作" else "通知渠道降噪",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (ready) StatusGreenText else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = state.privilege.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ready) {
                        StatusGreenText.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (state.progress != null) {
                    Text(
                        text = state.progress.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (ready) "Shizuku" else "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (ready) StatusGreenText else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ready) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF34C759),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 20.dp)
                        .size(72.dp),
                )
            }
        }
    }
}

@Composable
private fun GateActions(
    state: HomeUiState,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRefresh: () -> Unit,
    onApplyMute: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
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
                    OutlinedButton(onClick = onOpenShizuku, modifier = Modifier.weight(1f)) {
                        Text("打开 Shizuku")
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
                        enabled = state.plan.any { it.value != RuleAction.KEEP },
                        modifier = Modifier.weight(1f),
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
private fun StatRow(leftLabel: String, leftValue: String, rightLabel: String, rightValue: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(label = leftLabel, value = leftValue, modifier = Modifier.weight(1f))
        StatCard(label = rightLabel, value = rightValue, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun DeviceInfoCard(state: HomeUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoBlock("机型", Build.MODEL ?: "—")
            InfoBlock("系统", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
            InfoBlock("正在使用的特权", state.privilege.label)
            InfoBlock("自动静音", if (state.autoMuteOn) "已开启" else "关闭")
        }
    }
}

@Composable
private fun InfoBlock(title: String, value: String) {
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
