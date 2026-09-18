package app.quieta.feature.config

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quieta.R
import app.quieta.core.model.Rule
import app.quieta.core.model.RuleAction
import app.quieta.ui.component.HyperOsPopup
import app.quieta.ui.component.HyperOsPopupRow
import app.quieta.ui.component.QuietaPage
import app.quieta.ui.component.QuietaSwitch
import app.quieta.ui.component.QuietaWindowDialog
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ConfigScreen(
    modifier: Modifier = Modifier,
    blurEnabled: Boolean = true,
    viewModel: ConfigViewModel = viewModel(),
    onOpenEditor: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showMoreMenu by rememberSaveable { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)
                ?.use { stream -> stream.readBytes().decodeToString() }
            if (raw.isNullOrBlank()) {
                return@runCatching
            }
            val label = uri.lastPathSegment?.substringAfterLast('/') ?: "规则文件"
            viewModel.previewImportRaw(raw, sourceLabel = label)
        }.onFailure {
            // Message handled below when preview fails; catch decode path in ViewModel.
        }
    }

    if (state.selectionMode) {
        BackHandler { viewModel.exitSelection() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        QuietaPage(
            title = if (state.selectionMode) {
                if (state.selectedRuleIds.isEmpty()) "多选规则" else "已选择 ${state.selectedRuleIds.size} 项"
            } else {
                stringResource(R.string.config_title)
            },
            blurEnabled = blurEnabled,
            navigationIcon = if (state.selectionMode) {
                {
                    IconButton(onClick = viewModel::exitSelection) {
                        Icon(Icons.Outlined.Close, contentDescription = "退出多选")
                    }
                }
            } else {
                {}
            },
            actions = {
                if (state.selectionMode) {
                    TextButton(text = "全选", onClick = viewModel::selectAllRules)
                    TextButton(text = "清空", onClick = viewModel::clearSelection)
                } else {
                    if (state.rules.isNotEmpty()) {
                        IconButton(onClick = viewModel::enterSelection) {
                            Icon(
                                Icons.Outlined.Checklist,
                                contentDescription = "多选",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = "更多",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            },
        ) {
            item {
                TipCard(
                    text = "白名单（保留）优先于静音/降级；其余按更精确的规则优先。可匹配包名/包前缀/渠道id/名称。主页可先预览再静音。顶栏可导出/导入 JSON（合并或替换）。",
                )
            }

            if (state.rules.isEmpty()) {
                item {
                    Text(
                        text = "还没有规则。",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            val selectionMode = state.selectionMode
            val selectedIds = state.selectedRuleIds
            val whitelistRules = state.rules.filter { it.action == RuleAction.KEEP }
            val actionRules = state.rules.filterNot { it.action == RuleAction.KEEP }

            if (whitelistRules.isNotEmpty()) {
                item(key = "wl-group") {
                    SmallTitle(
                        text = "永不静音（白名单）",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    Card(modifier = Modifier.fillMaxWidth()) {
                        whitelistRules.forEachIndexed { index, rule ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MiuixTheme.colorScheme.dividerLine,
                                )
                            }
                            RuleRow(
                                rule = rule,
                                hitStat = state.hitStats[rule.id],
                                selectionMode = selectionMode,
                                selected = rule.id in selectedIds,
                                onSelectToggle = { viewModel.toggleRuleSelection(rule.id) },
                                onToggle = { viewModel.toggle(rule.id) },
                                onRemove = { viewModel.remove(rule.id) },
                                onEdit = {
                                    viewModel.openEditRule(rule)
                                    onOpenEditor()
                                },
                            )
                        }
                    }
                }
            }

            if (actionRules.isNotEmpty()) {
                item(key = "ac-group") {
                    SmallTitle(
                        text = "静音 / 降级",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    Card(modifier = Modifier.fillMaxWidth()) {
                        actionRules.forEachIndexed { index, rule ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MiuixTheme.colorScheme.dividerLine,
                                )
                            }
                            RuleRow(
                                rule = rule,
                                hitStat = state.hitStats[rule.id],
                                selectionMode = selectionMode,
                                selected = rule.id in selectedIds,
                                onSelectToggle = { viewModel.toggleRuleSelection(rule.id) },
                                onToggle = { viewModel.toggle(rule.id) },
                                onRemove = { viewModel.remove(rule.id) },
                                onEdit = {
                                    viewModel.openEditRule(rule)
                                    onOpenEditor()
                                },
                            )
                        }
                    }
                }
            }

            state.message?.let { msg ->
                item {
                    Text(
                        text = msg,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            item(key = "packs-title") {
                SmallTitle(
                    text = "规则包（默认合并，不删现有规则）",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            item(key = "packs") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    val packs = app.quieta.core.repo.RulePresetPacks.all
                    packs.forEachIndexed { index, pack ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.5.dp,
                                color = MiuixTheme.colorScheme.dividerLine,
                            )
                        }
                        BasicComponent(
                            title = pack.title,
                            summary = pack.description,
                            onClick = { viewModel.previewPresetPack(pack.id) },
                        )
                    }
                }
            }
        }

        if (!state.selectionMode) {
            FloatingActionButton(
                onClick = {
                    viewModel.openAddRule()
                    onOpenEditor()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 108.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "添加规则", tint = Color.White)
            }
        }
    }

    if (showMoreMenu) {
        HyperOsPopup(onDismissRequest = { showMoreMenu = false }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HyperOsPopupRow(
                    title = "导出全部规则",
                    subtitle = "分享 JSON（含 schemaVersion）",
                    selected = false,
                    showCheck = false,
                    onClick = {
                        showMoreMenu = false
                        viewModel.exportAndShare(onlySelected = false)
                    },
                )
                HyperOsPopupRow(
                    title = "导入规则文件",
                    subtitle = "合并导入（推荐）或替换全部",
                    selected = false,
                    showCheck = false,
                    onClick = {
                        showMoreMenu = false
                        importLauncher.launch(
                            arrayOf("application/json", "text/plain", "text/*", "*/*"),
                        )
                    },
                )
            }
        }
    }

    state.importPreview?.let { preview ->
        RuleImportDialog(
            preview = preview,
            onDismiss = viewModel::dismissImportPreview,
            onMerge = viewModel::confirmMergeImport,
            onReplace = viewModel::confirmReplaceImport,
        )
    }
}

@Composable
private fun RuleImportDialog(
    preview: RuleImportPreview,
    onDismiss: () -> Unit,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
) {
    var confirmReplace by remember { mutableStateOf(false) }
    QuietaWindowDialog(
        show = true,
        onDismissRequest = {
            confirmReplace = false
            onDismiss()
        },
        title = preview.title,
        summary = "新增 ${preview.addCount} · 跳过 ${preview.skipCount} · 保留现有 ${preview.keepCount}",
        cancelText = "取消",
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = "合并导入",
                    summary = "只追加新规则，不删除现有规则（推荐）",
                    onClick = onMerge,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MiuixTheme.colorScheme.dividerLine,
                )
                BasicComponent(
                    title = if (confirmReplace) "再次点击确认替换" else "替换全部（危险）",
                    summary = "删除全部现有规则，仅保留本文件/规则包",
                    onClick = {
                        if (confirmReplace) onReplace() else confirmReplace = true
                    },
                )
            }
        }
    }
}

/** Secondary host: reads draft from ConfigViewModel (status bar handled by QuietaPage). */
@Composable
fun ConfigRuleEditorScreen(
    viewModel: ConfigViewModel,
    onBack: () -> Unit,
    blurEnabled: Boolean = true,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draft = state.draft
    RuleEditorScreen(
        title = stringResource(if (state.editingRuleId == null) R.string.rule_add else R.string.rule_edit),
        packageInput = draft.packageName,
        onPackageInput = { viewModel.updateDraft(draft.copy(packageName = it)) },
        packagePrefixInput = draft.packagePrefix,
        onPackagePrefixInput = { viewModel.updateDraft(draft.copy(packagePrefix = it)) },
        channelIdExactInput = draft.channelIdExact,
        onChannelIdExactInput = { viewModel.updateDraft(draft.copy(channelIdExact = it)) },
        channelIdPrefixInput = draft.channelIdPrefix,
        onChannelIdPrefixInput = { viewModel.updateDraft(draft.copy(channelIdPrefix = it)) },
        nameInput = draft.nameContains,
        onNameInput = { viewModel.updateDraft(draft.copy(nameContains = it)) },
        matchName = draft.matchName,
        onMatchName = { viewModel.updateDraft(draft.copy(matchName = it)) },
        matchId = draft.matchId,
        onMatchId = { viewModel.updateDraft(draft.copy(matchId = it)) },
        action = draft.action,
        onAction = { viewModel.updateDraft(draft.copy(action = it)) },
        canSave = draft.packageName.isNotBlank() ||
            draft.packagePrefix.isNotBlank() ||
            draft.channelIdExact.isNotBlank() ||
            draft.channelIdPrefix.isNotBlank() ||
            draft.nameContains.isNotBlank() ||
            state.editingRuleId != null,
        message = state.message,
        onClose = {
            viewModel.closeRuleEditor()
            onBack()
        },
        onSave = { viewModel.saveEditor(onSaved = onBack) },
        blurEnabled = blurEnabled,
    )
}

/** InstallerX Revived: miuix TopAppBar (Close/Ok + large title), field cards, grouped rows. */
@Composable
private fun RuleEditorScreen(
    title: String,
    packageInput: String,
    onPackageInput: (String) -> Unit,
    packagePrefixInput: String,
    onPackagePrefixInput: (String) -> Unit,
    channelIdExactInput: String,
    onChannelIdExactInput: (String) -> Unit,
    channelIdPrefixInput: String,
    onChannelIdPrefixInput: (String) -> Unit,
    nameInput: String,
    onNameInput: (String) -> Unit,
    matchName: Boolean,
    onMatchName: (Boolean) -> Unit,
    matchId: Boolean,
    onMatchId: (Boolean) -> Unit,
    action: RuleAction,
    onAction: (RuleAction) -> Unit,
    canSave: Boolean,
    message: String?,
    onClose: () -> Unit,
    onSave: () -> Unit,
    blurEnabled: Boolean = true,
) {
    app.quieta.ui.component.QuietaPage(
        title = title,
        blurEnabled = blurEnabled,
        bottomPadding = 32.dp,
        itemSpacing = 0.dp,
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        actions = {
            IconButton(onClick = onSave, enabled = canSave) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = "保存",
                    tint = if (canSave) MiuixTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                )
            }
        },
    ) {
        item { FieldCard(value = packageInput, onValueChange = onPackageInput, placeholder = "包名（精确，可多个，逗号分隔）") }
        item { FieldCard(value = packagePrefixInput, onValueChange = onPackagePrefixInput, placeholder = "包名前缀") }
        item { FieldCard(value = channelIdExactInput, onValueChange = onChannelIdExactInput, placeholder = "渠道 ID（精确，可多个，逗号分隔）") }
        item { FieldCard(value = channelIdPrefixInput, onValueChange = onChannelIdPrefixInput, placeholder = "渠道 ID 前缀") }
        item { FieldCard(value = nameInput, onValueChange = onNameInput, placeholder = "关键词包含…") }

        item { SmallTitle("匹配") }
        item {
            Card(modifier = Modifier.padding(horizontal = 0.dp)) {
                Column {
                    SwitchRow("匹配名称", "关键词作用于渠道显示名", matchName, onMatchName)
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MiuixTheme.colorScheme.dividerLine,
                    )
                    SwitchRow("匹配渠道 ID", "关键词作用于渠道 id", matchId, onMatchId)
                }
            }
        }

        item { SmallTitle("动作") }
        item {
            Card {
                Column {
                    RuleAction.entries.forEachIndexed { index, item ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.5.dp,
                                color = MiuixTheme.colorScheme.dividerLine,
                            )
                        }
                        ActionSelectRow(
                            title = stringResource(
                                when (item) {
                                    RuleAction.MUTE -> R.string.rule_mute
                                    RuleAction.DOWNGRADE -> R.string.rule_downgrade
                                    RuleAction.KEEP -> R.string.rule_keep
                                },
                            ),
                            selected = action == item,
                            onClick = { onAction(item) },
                        )
                    }
                }
            }
        }

        val draftBroad = app.quieta.core.engine.BroadKeywords.isBroad(nameInput) ||
            app.quieta.core.engine.BroadKeywords.isBroad(channelIdPrefixInput) ||
            app.quieta.core.engine.BroadKeywords.isBroad(packagePrefixInput)
        if (draftBroad) {
            item {
                Text(
                    text = "关键词可能过宽，容易误伤。建议改为包前缀或渠道 ID 前缀。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB45309),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        message?.let {
            item {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MiuixTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun FieldCard(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = placeholder,
        useLabelAsPlaceholder = true,
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    BasicComponent(
        title = title,
        summary = subtitle,
        endActions = {
            QuietaSwitch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

@Composable
private fun ActionSelectRow(title: String, selected: Boolean, onClick: () -> Unit) {
    BasicComponent(
        modifier = Modifier.semantics { this.selected = selected },
        title = title,
        role = Role.RadioButton,
        onClick = onClick,
        endActions = {
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                )
            }
        },
    )
}

@Composable
private fun TipCard(text: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.primary.copy(alpha = 0.2f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.primary,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
    }
}

@Composable
private fun RuleRow(
    rule: Rule,
    hitStat: app.quieta.core.engine.RuleHitStat?,
    selectionMode: Boolean,
    selected: Boolean,
    onSelectToggle: () -> Unit,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
) {
    var showSamples by rememberSaveable(rule.id) { mutableStateOf(false) }
    val rowModifier = if (selectionMode) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelectToggle)
            .semantics { this.selected = selected }
    } else {
        Modifier.fillMaxWidth()
    }
    Column(modifier = rowModifier) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectionMode) {
                    SelectionDot(selected = selected)
                }
                Text(
                    text = ruleSummary(rule),
                    style = MiuixTheme.textStyles.title4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (hitStat?.broadKeyword == true && rule.action != RuleAction.KEEP) {
                    SurfacePill(text = "过宽", warning = true)
                }
                SurfacePill(
                    text = when (rule.action) {
                        RuleAction.MUTE -> "静音"
                        RuleAction.DOWNGRADE -> "降级"
                        RuleAction.KEEP -> "保留"
                    },
                )
            }
            if (!selectionMode) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietaSwitch(checked = rule.enabled, onCheckedChange = { onToggle() })
                    if (hitStat != null) {
                        val hitLabel = if (hitStat.effectiveCount == hitStat.matchCount) {
                            "命中 ${hitStat.matchCount}"
                        } else {
                            "匹配 ${hitStat.matchCount} · 生效 ${hitStat.effectiveCount}"
                        }
                        val canOpen = hitStat.samples.isNotEmpty()
                        Box(
                            modifier = Modifier
                                .background(
                                    if (canOpen) MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)
                                    else MiuixTheme.colorScheme.secondaryVariant.copy(alpha = 0.35f),
                                    CircleShape,
                                )
                                .clickable(enabled = canOpen) { showSamples = true }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = hitLabel,
                                style = MiuixTheme.textStyles.footnote2,
                                color = if (canOpen) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.rule_edit))
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Outlined.Delete, contentDescription = "删除")
                    }
                }
            } else if (hitStat != null && hitStat.samples.isNotEmpty()) {
                Text(
                    text = "命中 ${hitStat.samples.size} 条样本",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }

    if (showSamples && hitStat != null) {
        QuietaWindowDialog(
            show = true,
            onDismissRequest = { showSamples = false },
            title = "命中样本 ${hitStat.samples.size} 条",
            cancelText = "关闭",
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (hitStat.broadKeyword && rule.action != RuleAction.KEEP) {
                    Text(
                        text = "关键词过宽，可能误伤物流/客服/系统渠道。",
                        style = MiuixTheme.textStyles.body2,
                        color = Color(0xFFB45309),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    hitStat.samples.forEach { sample ->
                        Column {
                            Text(sample.appLabel, style = MiuixTheme.textStyles.body2)
                            Text(
                                text = sample.channelName + " (" + sample.channelId + ")",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionDot(selected: Boolean) {
    // Brand fixed blue for multi-select (see AGENTS semantic-color table).
    val stroke = Color(0xFF3482FF)
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(
                color = if (selected) stroke else stroke.copy(alpha = 0.18f),
                shape = CircleShape,
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

private fun ruleSummary(rule: Rule): String {
    val parts = buildList {
        rule.channelIdExact?.let {
            val list = app.quieta.core.engine.normalizeIdList(it)
            if (list.isNotEmpty()) add("id=" + list.joinToString("/"))
        }
        rule.channelIdPrefix?.let { add("id前缀=$it") }
        rule.packageName?.let {
            val list = app.quieta.core.engine.normalizeIdList(it)
            if (list.isNotEmpty()) add("包名=" + list.joinToString("/"))
        }
        rule.packagePrefix?.let { add("包前缀=$it") }
        rule.nameContains?.let {
            val scope = when {
                rule.matchName && rule.matchId -> "名称/id"
                rule.matchName -> "名称"
                rule.matchId -> "id"
                else -> "关键词"
            }
            add("${scope}含“$it”")
        }
    }
    return parts.joinToString(" · ").ifEmpty { "(空)" }
}

@Composable
private fun SurfacePill(text: String, warning: Boolean = false) {
    val bg = if (warning) Color(0xFFFFF1CC) else MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
    val fg = if (warning) Color(0xFFB45309) else MiuixTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .background(bg, CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = MiuixTheme.textStyles.footnote2, color = fg)
    }
}
