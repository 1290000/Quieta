package app.quieta.feature.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quieta.R
import app.quieta.core.model.Rule
import app.quieta.core.model.RuleAction
import app.quieta.ui.component.QuietaPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    modifier: Modifier = Modifier,
    blurEnabled: Boolean = true,
    viewModel: ConfigViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var nameInput by rememberSaveable { mutableStateOf("") }
    var packageInput by rememberSaveable { mutableStateOf("") }
    var packagePrefixInput by rememberSaveable { mutableStateOf("") }
    var channelIdExactInput by rememberSaveable { mutableStateOf("") }
    var channelIdPrefixInput by rememberSaveable { mutableStateOf("") }
    var matchName by rememberSaveable { mutableStateOf(true) }
    var matchId by rememberSaveable { mutableStateOf(true) }
    var actionInput by rememberSaveable { mutableStateOf(RuleAction.MUTE.name) }
    val draft = RuleDraft(
        nameContains = nameInput,
        packageName = packageInput,
        packagePrefix = packagePrefixInput,
        channelIdExact = channelIdExactInput,
        channelIdPrefix = channelIdPrefixInput,
        matchName = matchName,
        matchId = matchId,
        action = RuleAction.valueOf(actionInput),
    )
    fun loadDraft(next: RuleDraft, id: String?) {
        editingId = id
        nameInput = next.nameContains
        packageInput = next.packageName
        packagePrefixInput = next.packagePrefix
        channelIdExactInput = next.channelIdExact
        channelIdPrefixInput = next.channelIdPrefix
        matchName = next.matchName
        matchId = next.matchId
        actionInput = next.action.name
    }

    Box(modifier = modifier.fillMaxSize()) {
        QuietaPage(
            title = stringResource(R.string.config_title),
            blurEnabled = blurEnabled,
        ) {
            item {
                InfoBanner(
                    text = "白名单（保留）优先于静音/降级；其余按更精确的规则优先。可匹配包名/包前缀/渠道id/名称。主页可先预览再静音。",
                    onDismiss = { /* hint only */ },
                )
            }

            if (state.rules.isEmpty()) {
                item {
                    Text(
                        text = "还没有规则。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.rules, key = { it.id }) { rule ->
                RuleCard(
                    rule = rule,
                    hitStat = state.hitStats[rule.id],
                    onToggle = { viewModel.toggle(rule.id) },
                    onRemove = { viewModel.remove(rule.id) },
                    onEdit = {
                        loadDraft(viewModel.draftOf(rule), rule.id)
                        showAdd = true
                    },
                )
            }

            state.message?.let { msg ->
                item {
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        FloatingActionButton(
            onClick = {
                loadDraft(RuleDraft(), null)
                showAdd = true
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 108.dp)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "添加规则")
        }
    }

    if (showAdd) {
        ModalBottomSheet(
            onDismissRequest = { showAdd = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(if (editingId == null) R.string.rule_add else R.string.rule_edit),
                    style = MaterialTheme.typography.titleLarge,
                )
                OutlinedTextField(
                    value = packageInput,
                    onValueChange = { packageInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("包名（精确）") },
                    placeholder = { Text("app.quieta.notiflab.debug") },
                )
                OutlinedTextField(
                    value = packagePrefixInput,
                    onValueChange = { packagePrefixInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("包名前缀") },
                    placeholder = { Text("com.tencent.") },
                )
                OutlinedTextField(
                    value = channelIdExactInput,
                    onValueChange = { channelIdExactInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("渠道 ID（精确）") },
                )
                OutlinedTextField(
                    value = channelIdPrefixInput,
                    onValueChange = { channelIdPrefixInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("渠道 ID 前缀") },
                    placeholder = { Text("lab.marketing.") },
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("关键词包含…") },
                    placeholder = { Text("例如：推广") },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("匹配名称", modifier = Modifier.weight(1f))
                    Switch(checked = matchName, onCheckedChange = { matchName = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("匹配渠道 ID", modifier = Modifier.weight(1f))
                    Switch(checked = matchId, onCheckedChange = { matchId = it })
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    RuleAction.entries.forEachIndexed { index, action ->
                        SegmentedButton(
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                activeContentColor = MaterialTheme.colorScheme.primary,
                            ),
                            selected = draft.action == action,
                            onClick = { actionInput = action.name },
                            shape = SegmentedButtonDefaults.itemShape(index, RuleAction.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    when (action) {
                                        RuleAction.MUTE -> R.string.rule_mute
                                        RuleAction.DOWNGRADE -> R.string.rule_downgrade
                                        RuleAction.KEEP -> R.string.rule_keep
                                    },
                                ),
                            )
                        }
                    }
                }
                Text(
                    "说明：条件为「与」关系；保留=白名单，命中后不再静音。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val draftBroad = app.quieta.core.engine.BroadKeywords.isBroad(draft.nameContains) ||
                    app.quieta.core.engine.BroadKeywords.isBroad(draft.channelIdPrefix) ||
                    app.quieta.core.engine.BroadKeywords.isBroad(draft.packagePrefix)
                if (draftBroad) {
                    Text(
                        text = "⚠ 关键词可能过宽，容易误伤物流/客服/系统渠道。建议改为包前缀或渠道 ID 前缀。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB45309),
                    )
                }
                state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showAdd = false }) { Text("取消") }
                    TextButton(
                        onClick = { viewModel.saveRule(editingId, draft) { showAdd = false } },
                        enabled = draft.packageName.isNotBlank() ||
                            draft.packagePrefix.isNotBlank() ||
                            draft.channelIdExact.isNotBlank() ||
                            draft.channelIdPrefix.isNotBlank() ||
                            draft.nameContains.isNotBlank() ||
                            editingId != null,
                    ) { Text("确定") }
                }
            }
        }
    }
}

@Composable
private fun InfoBanner(text: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: Rule,
    hitStat: app.quieta.core.engine.RuleHitStat?,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ruleSummary(rule),
                    style = app.quieta.ui.theme.QuietaTextStyles.ruleTitle,
                    modifier = Modifier.weight(1f),
                )
                SurfacePill(
                    text = when (rule.action) {
                        RuleAction.MUTE -> "静音"
                        RuleAction.DOWNGRADE -> "降级"
                        RuleAction.KEEP -> "保留"
                    },
                )
            }
            if (hitStat != null) {
                var samplesExpanded by rememberSaveable(rule.id) { mutableStateOf(false) }
                var showFullSamples by rememberSaveable(rule.id) { mutableStateOf(false) }
                val previewSamples = hitStat.samples.take(3)
                Text(
                    text = if (hitStat.effectiveCount == hitStat.matchCount) {
                        "当前盘点命中 ${hitStat.matchCount} 个渠道"
                    } else {
                        "匹配 ${hitStat.matchCount} 个 · 最终生效 ${hitStat.effectiveCount} 个"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hitStat.matchCount == 0) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                if (hitStat.samples.isNotEmpty()) {
                    TextButton(
                        onClick = { samplesExpanded = !samplesExpanded },
                        modifier = Modifier.padding(start = 0.dp),
                    ) {
                        Text(if (samplesExpanded) "收起样本" else "展开样本（${hitStat.samples.size}）")
                    }
                }
                if (samplesExpanded) {
                    previewSamples.forEach { sample ->
                        Text(
                            text = "· ${sample.appLabel} / ${sample.channelName} (${sample.channelId})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    if (hitStat.samples.size > previewSamples.size) {
                        TextButton(onClick = { showFullSamples = true }) {
                            Text("查看完整 ${hitStat.samples.size} 条")
                        }
                    }
                }
                if (hitStat.broadKeyword && rule.action != RuleAction.KEEP) {
                    Text(
                        text = "⚠ 关键词过宽，可能误伤",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFB45309),
                    )
                }
                if (showFullSamples) {
                    AlertDialog(
                        onDismissRequest = { showFullSamples = false },
                        title = { Text("命中样本（${hitStat.samples.size}）") },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 360.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                hitStat.samples.forEach { sample ->
                                    Text(
                                        text = sample.appLabel + " / " + sample.channelName + "\n" + sample.channelId,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showFullSamples = false }) { Text("关闭") }
                        },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.padding(start = 4.dp),
                    colors = app.quieta.ui.component.installerLikeSwitchColors(),
                )
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
private fun SurfacePill(text: String) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}
