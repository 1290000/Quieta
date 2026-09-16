package app.quieta.feature.config

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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quieta.R
import app.quieta.core.model.Rule
import app.quieta.core.model.RuleAction
import app.quieta.ui.component.QuietaPage
import app.quieta.ui.component.QuietaSwitch
import top.yukonga.miuix.kmp.basic.Button
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

    Box(modifier = modifier.fillMaxSize()) {
        QuietaPage(
            title = stringResource(R.string.config_title),
            blurEnabled = blurEnabled,
        ) {
            item {
                TipCard(
                    text = "白名单（保留）优先于静音/降级；其余按更精确的规则优先。可匹配包名/包前缀/渠道id/名称。主页可先预览再静音。",
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

            val whitelistRules = state.rules.filter { it.action == RuleAction.KEEP }
            val actionRules = state.rules.filterNot { it.action == RuleAction.KEEP }

            if (whitelistRules.isNotEmpty()) {
                item(key = "wl-group") {
                    SmallTitle(
                        text = "永不静音（白名单）",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    // InstallerX MiuixPrivPage: one Card hosts all option rows.
                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                        whitelistRules.forEachIndexed { index, rule ->
                            if (index > 0) {
                                top.yukonga.miuix.kmp.basic.HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MiuixTheme.colorScheme.dividerLine,
                                )
                            }
                            RuleRow(
                                rule = rule,
                                hitStat = state.hitStats[rule.id],
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
                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                        actionRules.forEachIndexed { index, rule ->
                            if (index > 0) {
                                top.yukonga.miuix.kmp.basic.HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MiuixTheme.colorScheme.dividerLine,
                                )
                            }
                            RuleRow(
                                rule = rule,
                                hitStat = state.hitStats[rule.id],
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
        }

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
    // Same chrome as other Quieta secondary pages / InstallerX edit: TopAppBar large title.
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
        item { FieldCard(value = packageInput, onValueChange = onPackageInput, placeholder = "包名（精确）") }
        item { FieldCard(value = packagePrefixInput, onValueChange = onPackagePrefixInput, placeholder = "包名前缀") }
        item { FieldCard(value = channelIdExactInput, onValueChange = onChannelIdExactInput, placeholder = "渠道 ID（精确）") }
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
    // InstallerX MiuixHintTextField: standalone field with side padding, no nested card chrome.
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
    top.yukonga.miuix.kmp.basic.BasicComponent(
        title = title,
        summary = subtitle,
        endActions = {
            QuietaSwitch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

@Composable
private fun ActionSelectRow(title: String, selected: Boolean, onClick: () -> Unit) {
    top.yukonga.miuix.kmp.basic.BasicComponent(
        modifier = Modifier.semantics { this.selected = selected },
        title = title,
        role = androidx.compose.ui.semantics.Role.RadioButton,
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
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
) {
    var showSamples by rememberSaveable(rule.id) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        }
    }

    if (showSamples && hitStat != null) {
        Dialog(onDismissRequest = { showSamples = false }) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "命中样本 ${hitStat.samples.size} 条",
                            style = MiuixTheme.textStyles.title4,
                            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                        )
                        IconButton(onClick = { showSamples = false }) {
                            Icon(Icons.Outlined.Close, contentDescription = "关闭")
                        }
                    }
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                .heightIn(max = 360.dp)
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
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun ruleSummary(rule: Rule): String {
    val parts = buildList {
        rule.channelIdExact?.let { add("id=$it") }
        rule.channelIdPrefix?.let { add("id前缀=$it") }
        rule.packageName?.let { add("包名=$it") }
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
