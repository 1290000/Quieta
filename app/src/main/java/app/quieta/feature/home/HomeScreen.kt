package app.quieta.feature.home

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.displaySmall,
            )
        }

        item {
            StatusCard(
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
            )
        }

        state.error?.let { message ->
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (!state.loading && state.apps.isEmpty() && state.error == null && state.gate == PrivilegeGate.READY) {
            item {
                Text(
                    text = "未发现带通知渠道的应用（或读取失败）。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(state.apps, key = { it.packageName }) { app ->
            AppChannelCard(app = app, plan = state.plan)
        }
    }
}

@Composable
private fun StatusCard(
    state: HomeUiState,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.home_status_ready),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = state.privilege.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
            state.progress?.let { progress ->
                Text(text = progress, style = MaterialTheme.typography.bodySmall)
            }
            when (state.gate) {
                PrivilegeGate.CHECKING -> Unit
                PrivilegeGate.NEED_PERMISSION -> {
                    Text(
                        text = "需要授权后才能读取其它应用的通知渠道。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = onRequestPermission) {
                        Text("请求 Shizuku 授权")
                    }
                }
                PrivilegeGate.SHIZUKU_UNAVAILABLE -> {
                    Text(
                        text = "请先安装并启动 Shizuku（无线调试或 Root），然后重试。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onOpenShizuku) { Text("打开 Shizuku") }
                        TextButton(onClick = onRefresh) { Text("重试") }
                    }
                }
                PrivilegeGate.READY -> {
                    TextButton(onClick = onRefresh) { Text("刷新盘点") }
                }
            }
        }
    }
}

@Composable
private fun AppChannelCard(
    app: AppChannels,
    plan: Map<Channel, RuleAction>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(app.appLabel, style = MaterialTheme.typography.titleMedium)
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
        RuleAction.MUTE -> MaterialTheme.colorScheme.errorContainer
        RuleAction.DOWNGRADE -> MaterialTheme.colorScheme.tertiaryContainer
    }
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
