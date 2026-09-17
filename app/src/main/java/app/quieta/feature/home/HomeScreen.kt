package app.quieta.feature.home

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quieta.R
import app.quieta.core.model.Channel
import app.quieta.core.model.RuleAction
import app.quieta.core.engine.QuietMode
import app.quieta.ui.component.HyperOsPopup
import app.quieta.ui.component.HyperOsPopupDivider
import app.quieta.ui.component.HyperOsPopupRow
import app.quieta.ui.component.QuietaPage
import app.quieta.ui.component.PressableCard
import rikka.shizuku.Shizuku
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Card as MiuixCard

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
    onOpenMutePreview: () -> Unit = {},
    onOpenQuietChannels: (app.quieta.core.engine.QuietMode) -> Unit = {},
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

    var previewNavArmed by remember { mutableStateOf(true) }
    LaunchedEffect(state.mutePreview) {
        val preview = state.mutePreview
        if (preview == null) {
            previewNavArmed = true
        } else if (previewNavArmed) {
            previewNavArmed = false
            onOpenMutePreview()
        }
    }

    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    var showDisplaySheet by rememberSaveable { mutableStateOf(false) }
    val display by viewModel.displayPrefs.collectAsStateWithLifecycle()
    val selectionMode = state.selectionMode
    val selectedCount = state.selectedChannelKeys.size

    if (selectionMode) {
        androidx.activity.compose.BackHandler {
            viewModel.exitSelection()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        QuietaPage(
            title = if (selectionMode) {
                if (selectedCount == 0) "多选渠道" else "已选择 $selectedCount 项"
            } else {
                stringResource(R.string.home_title)
            },
            blurEnabled = blurEnabled,
            navigationIcon = if (selectionMode) {
                {
                    IconButton(onClick = viewModel::exitSelection) {
                        Icon(Icons.Outlined.Close, contentDescription = "退出多选")
                    }
                }
            } else {
                {}
            },
            actions = {
                if (selectionMode) {
                    TextButton(onClick = viewModel::selectAllVisible) {
                        Text("全选")
                    }
                    TextButton(onClick = viewModel::clearSelection) {
                        Text("清空")
                    }
                }
            },
        ) {
        item(key = "status") {
            StatusGrid(
                gate = state.gate,
                privilege = state.privilege,
                authorizerCount = listOf(state.rootAvailable, state.shizukuAuthorized, state.dhizukuAvailable).count { it },
                rulesCount = state.rulesCount,
                onOpenPrivilege = onOpenPrivilege,
                onOpenConfig = onOpenConfig,
            )
        }

        item(key = "actions") {
            GateActions(
                state = state,
                selectionMode = selectionMode,
                onRequestPermission = {
                    runCatching { Shizuku.requestPermission(REQ_SHIZUKU) }
                },
                onOpenPrivilege = onOpenPrivilege,
                onRefresh = viewModel::refresh,
                onApplyMute = viewModel::requestBatchMutePreview,
                onUndo = viewModel::undoLastBatch,
                undoLabel = state.undoLabel,
                canUndo = state.canUndoLastBatch,
            )
        }

        item(key = "device") {
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
            item(key = "list-header") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = if (selectionMode) "选择渠道" else "通知渠道",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (!selectionMode) {
                            IconButton(onClick = { viewModel.enterSelection() }) {
                                Icon(
                                    Icons.Outlined.Checklist,
                                    contentDescription = "多选",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = viewModel::collapseAll) {
                            Icon(Icons.Outlined.ExpandLess, contentDescription = "全部收起")
                        }
                        IconButton(onClick = viewModel::expandAllVisible) {
                            Icon(Icons.Outlined.ExpandMore, contentDescription = "全部展开")
                        }
                        IconButton(onClick = { showDisplaySheet = true }) {
                            Icon(
                                Icons.Outlined.Visibility,
                                contentDescription = "显示选项",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(
                                Icons.Outlined.Tune,
                                contentDescription = "筛选与排序",
                                tint = if (state.filters.isActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("搜索应用、包名或渠道") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "清除搜索",
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                    )
                    if (state.listSummary.isNotEmpty()) {
                        Text(
                            text = state.listSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (state.listItems.isEmpty()) {
                item(key = "list-empty") {
                    Text(
                        text = "没有匹配的应用或渠道。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.listItems, key = { it.app.packageName }) { item ->
                    AppChannelCard(
                        item = item,
                        plan = state.plan,
                        display = display,
                        selectionMode = selectionMode,
                        selectedKeys = state.selectedChannelKeys,
                        onToggleExpand = { viewModel.toggleExpanded(item.app.packageName) },
                        onToggleAppSelection = { viewModel.toggleAppSelection(item.app.packageName) },
                        onLongPressApp = { viewModel.enterSelection(item.app.packageName) },
                        onToggleChannelSelection = viewModel::toggleChannelSelection,
                        onChannelAction = viewModel::applyChannelAction,
                        onMuteApp = { viewModel.muteApp(item.app.packageName) },
                        onRestoreApp = { viewModel.restoreApp(item.app.packageName) },
                    )
                }
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

    if (showDisplaySheet) {
        HomeDisplaySheet(
            display = display,
            onDismiss = { showDisplaySheet = false },
            onToggle = viewModel::toggleDisplayPref,
            onReset = viewModel::resetDisplayPrefs,
        )
    }

    if (showFilterSheet) {
        FilterSortSheet(
            filters = state.filters,
            sort = state.sort,
            onDismiss = { showFilterSheet = false },
            onToggleHasHigh = { viewModel.toggleFilter { it.copy(hasHigh = !it.hasHigh) } },
            onToggleHasNone = { viewModel.toggleFilter { it.copy(hasNone = !it.hasNone) } },
            onToggleWillMute = { viewModel.toggleFilter { it.copy(willMute = !it.willMute) } },
            onToggleOnlyUser = { viewModel.toggleFilter { it.copy(onlyUser = !it.onlyUser, onlySystem = false) } },
            onToggleOnlySystem = { viewModel.toggleFilter { it.copy(onlySystem = !it.onlySystem, onlyUser = false) } },
            onToggleOnlyMarketing = { viewModel.toggleFilter { it.copy(onlyLikelyMarketing = !it.onlyLikelyMarketing) } },
            onToggleOnlySilent = { viewModel.toggleFilter { it.copy(onlySilentAllowed = !it.onlySilentAllowed, onlyQuietWithSound = false) } },
            onToggleOnlyQuietWithSound = { viewModel.toggleFilter { it.copy(onlyQuietWithSound = !it.onlyQuietWithSound, onlySilentAllowed = false) } },
            onOpenQuietChannels = onOpenQuietChannels,
            onSoundFilter = { sound -> viewModel.toggleFilter { it.copy(sound = sound) } },
            onSortChange = viewModel::setSort,
            onResetFilters = {
                viewModel.toggleFilter { ChannelListFilters() }
            },
        )
    }

        if (selectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 28.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                SelectionActionBar(
                    count = selectedCount,
                    enabled = !state.checkingPrivilege && selectedCount > 0,
                    busy = state.progress != null,
                    onMute = { viewModel.applySelectionAction(RuleAction.MUTE) },
                    onDowngrade = { viewModel.applySelectionAction(RuleAction.DOWNGRADE) },
                    onKeep = { viewModel.applySelectionAction(RuleAction.KEEP) },
                )
            }
        }
    }
}

@Composable
private fun SelectionActionBar(
    count: Int,
    enabled: Boolean,
    busy: Boolean,
    onMute: () -> Unit,
    onDowngrade: () -> Unit,
    onKeep: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (busy) "处理中…" else "已选 $count",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(modifier = Modifier.weight(0.4f))
            Button(
                onClick = onMute,
                enabled = enabled && !busy,
                modifier = Modifier.weight(1.2f),
            ) {
                Text("静音")
            }
            OutlinedButton(
                onClick = onDowngrade,
                enabled = enabled && !busy,
                modifier = Modifier.weight(1f),
            ) {
                Text("降级")
            }
            OutlinedButton(
                onClick = onKeep,
                enabled = enabled && !busy,
                modifier = Modifier.weight(1f),
            ) {
                Text("保留")
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
    // InstallerX: known-available stays green even while a background re-probe runs.
    // Gray is only for a true first-run CHECKING frame with no cached privilege.
    val checking = gate == PrivilegeGate.CHECKING
    val active = privilege.available
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    )
                    Spacer(modifier = Modifier.height(36.dp))
                    Text(
                        text = if (checking) "…" else privilege.label,
                        style = app.quieta.ui.theme.QuietaTextStyles.statusDetail,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                value = if (checking) "…" else authorizerCount.toString(),
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
    onUndo: () -> Unit = {},
    undoLabel: String? = null,
    canUndo: Boolean = false,
    selectionMode: Boolean = false,
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
                PrivilegeGate.CHECKING, PrivilegeGate.READY -> {
                    IconButton(onClick = onRefresh, enabled = !state.checkingPrivilege) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                    if (canUndo) {
                        IconButton(onClick = onUndo, enabled = !state.checkingPrivilege) {
                            Icon(Icons.Outlined.Undo, contentDescription = undoLabel ?: "撤销")
                        }
                    }
                    Button(
                        onClick = onApplyMute,
                        enabled = !selectionMode && !state.checkingPrivilege && state.gate == PrivilegeGate.READY &&
                            state.plan.any { it.value != RuleAction.KEEP } &&
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
private fun FilterSortEntry(
    filters: ChannelListFilters,
    sort: ChannelSort,
    onClick: () -> Unit,
) {
    val parts = buildList {
        if (filters.hasHigh) add("含 HIGH")
        if (filters.hasNone) add("含 NONE")
        if (filters.willMute) add("将静音")
        add(
            "排序 " + when (sort) {
                ChannelSort.CHANNEL_COUNT -> "渠道数"
                ChannelSort.NAME -> "名称"
                ChannelSort.PACKAGE -> "包名"
                ChannelSort.MAX_IMPORTANCE -> "最高级"
            },
        )
    }
    val title = if (filters.isActive) "筛选与排序" else "筛选与排序"
    val summary = if (filters.isActive) parts.joinToString(" · ") else "含条件或排序时可在此调整"
    PressableCard(
        onClick = onClick,
        cornerRadius = 16.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = "打开筛选",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FilterSortSheet(
    filters: ChannelListFilters,
    sort: ChannelSort,
    onDismiss: () -> Unit,
    onToggleHasHigh: () -> Unit,
    onToggleHasNone: () -> Unit,
    onToggleWillMute: () -> Unit,
    onToggleOnlyUser: () -> Unit,
    onToggleOnlySystem: () -> Unit,
    onToggleOnlyMarketing: () -> Unit,
    onToggleOnlySilent: () -> Unit,
    onToggleOnlyQuietWithSound: () -> Unit,
    onOpenQuietChannels: (QuietMode) -> Unit,
    onSoundFilter: (SoundFilter) -> Unit,
    onSortChange: (ChannelSort) -> Unit,
    onResetFilters: () -> Unit,
) {
    var showSoundPicker by remember { mutableStateOf(false) }
    HyperOsPopup(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HyperOsPopupRow("含 HIGH", selected = filters.hasHigh, onClick = onToggleHasHigh)
            HyperOsPopupRow("含 NONE", selected = filters.hasNone, onClick = onToggleHasNone)
            HyperOsPopupRow("将静音", selected = filters.willMute, onClick = onToggleWillMute)
            HyperOsPopupRow("仅用户应用", selected = filters.onlyUser, onClick = onToggleOnlyUser)
            HyperOsPopupRow("仅系统应用", selected = filters.onlySystem, onClick = onToggleOnlySystem)
            HyperOsPopupRow("疑似营销", selected = filters.onlyLikelyMarketing, onClick = onToggleOnlyMarketing)
            HyperOsPopupRow(
                title = "静默仍开",
                selected = filters.onlySilentAllowed,
                subtitle = "允许通知开，声音/悬浮/振动关",
                onClick = {
                    onToggleOnlySilent()
                    onOpenQuietChannels(QuietMode.SILENT_NO_SOUND)
                },
            )
            HyperOsPopupRow(
                title = "仅声音·无横幅",
                selected = filters.onlyQuietWithSound,
                subtitle = "允许通知开，有声音，不横幅",
                onClick = {
                    onToggleOnlyQuietWithSound()
                    onOpenQuietChannels(QuietMode.QUIET_WITH_SOUND)
                },
            )
            HyperOsPopupRow(
                title = "声音",
                selected = filters.sound != SoundFilter.ALL,
                subtitle = when (filters.sound) {
                    SoundFilter.ON -> "声音开"
                    SoundFilter.OFF -> "声音关"
                    SoundFilter.ALL -> null
                },
                onClick = { showSoundPicker = true },
                showCheck = false,
            )
            HyperOsPopupRow(
                title = "重置筛选",
                selected = false,
                showCheck = false,
                onClick = onResetFilters,
            )
            HyperOsPopupDivider()
            val sortOptions = listOf(
                ChannelSort.CHANNEL_COUNT to "渠道数",
                ChannelSort.NAME to "名称",
                ChannelSort.PACKAGE to "包名",
                ChannelSort.MAX_IMPORTANCE to "最高级",
            )
            sortOptions.forEach { (value, name) ->
                HyperOsPopupRow(
                    title = name,
                    selected = sort == value,
                    onClick = { onSortChange(value) },
                )
            }
        }
    }
    if (showSoundPicker) {
        HyperOsPopup(onDismissRequest = { showSoundPicker = false }, topPadding = 120.dp) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HyperOsPopupRow("声音开", selected = filters.sound == SoundFilter.ON, onClick = { onSoundFilter(SoundFilter.ON); showSoundPicker = false })
                HyperOsPopupRow("声音关", selected = filters.sound == SoundFilter.OFF, onClick = { onSoundFilter(SoundFilter.OFF); showSoundPicker = false })
                HyperOsPopupDivider()
                HyperOsPopupRow("全部", selected = filters.sound == SoundFilter.ALL, onClick = { onSoundFilter(SoundFilter.ALL); showSoundPicker = false })
            }
        }
    }
}

@Composable
private fun HomeDisplaySheet(
    display: HomeDisplayPrefs,
    onDismiss: () -> Unit,
    onToggle: (String) -> Unit,
    onReset: () -> Unit,
) {
    HyperOsPopup(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HyperOsPopupRow("应用图标", selected = display.showAppIcon, onClick = { onToggle("appIcon") })
            HyperOsPopupRow("应用名", selected = display.showAppName, onClick = { onToggle("appName") })
            HyperOsPopupRow("包名", selected = display.showPackageName, onClick = { onToggle("packageName") })
            HyperOsPopupRow("渠道数", selected = display.showChannelCount, onClick = { onToggle("channelCount") })
            HyperOsPopupRow("渠道名", selected = display.showChannelName, onClick = { onToggle("channelName") })
            HyperOsPopupRow("渠道 ID", selected = display.showChannelId, onClick = { onToggle("channelId") })
            HyperOsPopupRow("importance", selected = display.showImportance, onClick = { onToggle("importance") })
            HyperOsPopupRow("声音点", selected = display.showSoundDot, onClick = { onToggle("soundDot") })
            HyperOsPopupRow("振动", selected = display.showVibration, onClick = { onToggle("vibration") })
            HyperOsPopupRow("规则预览", selected = display.showRulePreview, onClick = { onToggle("rulePreview") })
            HyperOsPopupRow("疑似营销", selected = display.showMarketingTag, onClick = { onToggle("marketingTag") })
            HyperOsPopupRow("应用操作入口", selected = display.showAppActions, onClick = { onToggle("appActions") })
            HyperOsPopupDivider()
            HyperOsPopupRow("恢复默认显示", selected = false, showCheck = false, onClick = onReset)
        }
    }
}

@Composable
private fun HomeAppIcon(packageName: String) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawable.toBitmap(96, 96).asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = packageName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppChannelCard(
    item: ChannelListAppItem,
    plan: Map<Channel, RuleAction>,
    display: HomeDisplayPrefs,
    selectionMode: Boolean = false,
    selectedKeys: Set<String> = emptySet(),
    onToggleExpand: () -> Unit,
    onToggleAppSelection: () -> Unit = {},
    onLongPressApp: () -> Unit = {},
    onToggleChannelSelection: (packageName: String, channelId: String) -> Unit = { _, _ -> },
    onChannelAction: (Channel, RuleAction) -> Unit,
    onMuteApp: () -> Unit,
    onRestoreApp: () -> Unit,
) {
    var actionTarget by remember { mutableStateOf<Channel?>(null) }
    var showAppActions by remember { mutableStateOf(false) }
    val appSelectedCount = item.app.channels.count { ch ->
        (item.app.packageName + "|" + ch.id) in selectedKeys
    }
    // Multi-select: header body selects the app; chevron always expands/collapses.
    val headerClick: () -> Unit =
        if (selectionMode) onToggleAppSelection else onToggleExpand
    val header: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (selectionMode) {
                SelectionCheck(
                    selected = appSelectedCount == item.app.channels.size && item.app.channels.isNotEmpty(),
                )
            } else if (display.showAppIcon) {
                HomeAppIcon(packageName = item.app.packageName)
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                if (display.showAppName) {
                    Text(item.app.appLabel, style = MaterialTheme.typography.titleLarge)
                }
                val meta = buildList {
                    if (display.showPackageName) add(item.app.packageName)
                    if (display.showChannelCount) add("${item.app.channels.size} 个渠道")
                    if (selectionMode && appSelectedCount > 0) add("已选 $appSelectedCount")
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!selectionMode && item.expanded && display.showAppActions) {
                IconButton(onClick = { showAppActions = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "应用操作")
                }
            }
            IconButton(onClick = onToggleExpand) {
                Icon(
                    imageVector = if (item.expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (item.expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (item.expanded) {
        // Expanded: static surface — no whole-card tilt while reading/acting on channels.
        MiuixCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = headerClick),
                ) {
                    header()
                }
                Spacer(modifier = Modifier.height(8.dp))
                item.channels.forEachIndexed { index, channel ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                    ChannelRow(
                        channel = channel,
                        plannedAction = plan[channel] ?: RuleAction.KEEP,
                        display = display,
                        selectionMode = selectionMode,
                        selected = (item.app.packageName + "|" + channel.id) in selectedKeys,
                        onClick = {
                            if (selectionMode) {
                                onToggleChannelSelection(item.app.packageName, channel.id)
                            } else {
                                actionTarget = channel
                            }
                        },
                        likelyMarketing = item.likelyMarketingChannelIds.contains(channel.id),
                    )
                }
            }
        }
    } else {
        // Collapsed: keep InstallerX press feedback on the compact app header only.
        PressableCard(
            onClick = headerClick,
            onLongClick = onLongPressApp,
            cornerRadius = 20.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                header()
            }
        }
    }

    if (showAppActions) {
        AppActionSheet(
            appLabel = item.app.appLabel,
            onDismiss = { showAppActions = false },
            onMuteApp = {
                showAppActions = false
                onMuteApp()
            },
            onRestoreApp = {
                showAppActions = false
                onRestoreApp()
            },
        )
    }

    actionTarget?.let { channel ->
        ChannelActionSheet(
            channel = channel,
            onDismiss = { actionTarget = null },
            onAction = { action ->
                onChannelAction(channel, action)
                actionTarget = null
            },
        )
    }
}

@Composable
private fun AppActionSheet(
    appLabel: String,
    onDismiss: () -> Unit,
    onMuteApp: () -> Unit,
    onRestoreApp: () -> Unit,
) {
    HyperOsPopup(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HyperOsPopupRow(
                title = "整应用静音",
                selected = false,
                showCheck = false,
                subtitle = "全部渠道 importance → NONE",
                onClick = onMuteApp,
            )
            HyperOsPopupRow(
                title = "整应用恢复",
                selected = false,
                showCheck = false,
                subtitle = "全部渠道 importance → DEFAULT",
                onClick = onRestoreApp,
            )
        }
    }
}

@Composable
private fun ChannelActionSheet(
    channel: Channel,
    onDismiss: () -> Unit,
    onAction: (RuleAction) -> Unit,
) {
    HyperOsPopup(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HyperOsPopupRow(
                title = "静音",
                selected = false,
                showCheck = false,
                subtitle = "importance → NONE",
                onClick = { onAction(RuleAction.MUTE) },
            )
            HyperOsPopupRow(
                title = "降级",
                selected = false,
                showCheck = false,
                subtitle = "importance → LOW",
                onClick = { onAction(RuleAction.DOWNGRADE) },
            )
            HyperOsPopupRow(
                title = "恢复",
                selected = false,
                showCheck = false,
                subtitle = "importance → DEFAULT",
                onClick = { onAction(RuleAction.KEEP) },
            )
        }
    }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    plannedAction: RuleAction,
    display: HomeDisplayPrefs,
    onClick: () -> Unit,
    likelyMarketing: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
) {
    val status = remember(channel.importance) { ChannelLiveStatus.from(channel.importance) }
    val secondary = remember(
        status,
        plannedAction,
        channel.id,
        likelyMarketing,
        display.showChannelName,
        display.showChannelId,
        display.showRulePreview,
        display.showMarketingTag,
        display.showVibration,
    ) {
        buildList {
            if (display.showChannelId) add(channel.id)
            if (display.showVibration) {
                add(if (channel.vibrationEnabled) "振动开" else "振动关")
            }
            if (display.showMarketingTag && likelyMarketing) add("疑似营销")
            if (display.showRulePreview && plannedAction != RuleAction.KEEP) {
                add(
                    "规则 " + when (plannedAction) {
                        RuleAction.MUTE -> "将静音"
                        RuleAction.DOWNGRADE -> "将降级"
                        RuleAction.KEEP -> ""
                    },
                )
            }
        }.joinToString(" · ")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selectionMode) {
            SelectionCheck(selected = selected)
        }
        Column(modifier = Modifier.weight(1f)) {
            if (display.showChannelName) {
                Text(channel.name, style = MaterialTheme.typography.bodyLarge)
            }
            if (secondary.isNotBlank()) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (display.showImportance) {
            LiveStatusChip(status)
        }
        if (display.showSoundDot) {
            Spacer(modifier = Modifier.width(6.dp))
            SoundDot(enabled = channel.soundEnabled)
        }
    }
}

private enum class ChannelLiveStatus(val label: String) {
    MUTED("静音"),
    DOWNGRADED("降级"),
    NORMAL("正常"),
    ;

    companion object {
        fun from(importance: app.quieta.core.model.ChannelImportance): ChannelLiveStatus = when (importance) {
            app.quieta.core.model.ChannelImportance.NONE -> MUTED
            app.quieta.core.model.ChannelImportance.MIN,
            app.quieta.core.model.ChannelImportance.LOW,
            -> DOWNGRADED
            app.quieta.core.model.ChannelImportance.DEFAULT,
            app.quieta.core.model.ChannelImportance.HIGH,
            -> NORMAL
        }
    }
}

@Composable
private fun LiveStatusChip(status: ChannelLiveStatus) {
    val container = when (status) {
        ChannelLiveStatus.MUTED -> Color(0xFFFFE5E1)
        ChannelLiveStatus.DOWNGRADED -> Color(0xFFFFF1CC)
        ChannelLiveStatus.NORMAL -> MiuixTheme.colorScheme.secondaryVariant
    }
    Box(
        modifier = Modifier
            .background(container, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(status.label, style = MaterialTheme.typography.labelMedium)
    }
}

/** File-manager style circular check for multi-select. */
@Composable
private fun SelectionCheck(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(
                if (selected) Color(0xFF3482FF)
                else MaterialTheme.colorScheme.surfaceVariant,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** Sound state on a single channel: gray = on, red = off (before the status pill). */
@Composable
private fun SoundDot(enabled: Boolean) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(
                color = if (enabled) Color(0xFF8E8E93) else Color(0xFFFF3B30),
                shape = CircleShape,
            ),
    )
}

