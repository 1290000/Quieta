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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
                item(key = "wl-title") {
                    SmallTitle(
                        text = "永不静音（白名单）",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                items(whitelistRules, key = { "wl-" + it.id }) { rule ->
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
            }

            if (actionRules.isNotEmpty()) {
                item(key = "ac-title") {
                    SmallTitle(
                        text = "静音 / 降级",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                items(actionRules, key = { "ac-" + it.id }) { rule ->
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
                loadDraft(RuleDraft(), null)
                showAdd = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 108.dp)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "添加规则", tint = Color.White)
        }
    }

    if (showAdd) {
        Dialog(onDismissRequest = { showAdd = false }) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(if (editingId == null) R.string.rule_add else R.string.rule_edit),
                        style = MiuixTheme.textStyles.title4,
                    )
                    TextField(
                        value = packageInput,
                        onValueChange = { packageInput = it },
                        label = "包名（精确）",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = packagePrefixInput,
                        onValueChange = { packagePrefixInput = it },
                        label = "包名前缀",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = channelIdExactInput,
                        onValueChange = { channelIdExactInput = it },
                        label = "渠道 ID（精确）",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = channelIdPrefixInput,
                        onValueChange = { channelIdPrefixInput = it },
                        label = "渠道 ID 前缀",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = "关键词包含…",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("匹配名称", modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.body2)
                        QuietaSwitch(checked = matchName, onCheckedChange = { matchName = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("匹配渠道 ID", modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.body2)
                        QuietaSwitch(checked = matchId, onCheckedChange = { matchId = it })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RuleAction.entries.forEach { action ->
                            val selected = draft.action == action
                            Button(
                                onClick = { actionInput = action.name },
                                colors = if (selected) {
                                    top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors(
                                        color = MiuixTheme.colorScheme.primary,
                                        contentColor = Color.White,
                                    )
                                } else {
                                    top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors()
                                },
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
                    val draftBroad = app.quieta.core.engine.BroadKeywords.isBroad(draft.nameContains) ||
                        app.quieta.core.engine.BroadKeywords.isBroad(draft.channelIdPrefix) ||
                        app.quieta.core.engine.BroadKeywords.isBroad(draft.packagePrefix)
                    if (draftBroad) {
                        Text(
                            text = "关键词可能过宽，容易误伤。建议改为包前缀或渠道 ID 前缀。",
                            style = MiuixTheme.textStyles.body2,
                            color = Color(0xFFB45309),
                        )
                    }
                    state.message?.let {
                        Text(it, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.primary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(text = "取消", onClick = { showAdd = false })
                        TextButton(text = "确定", onClick = { viewModel.saveRule(editingId, draft) { showAdd = false } }, enabled = draft.packageName.isNotBlank() ||
                                draft.packagePrefix.isNotBlank() ||
                                draft.channelIdExact.isNotBlank() ||
                                draft.channelIdPrefix.isNotBlank() ||
                                draft.nameContains.isNotBlank() ||
                                editingId != null)
                    }
                }
            }
        }
    }
}

@Composable
private fun TipCard(text: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.primary,
        )
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
    var showSamples by rememberSaveable(rule.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                QuietaSwitch(checked = rule.enabled, onCheckedChange = { onToggle() })
                if (hitStat != null) {
                    val hitLabel = if (hitStat.effectiveCount == hitStat.matchCount) {
                        "命中 ${hitStat.matchCount}"
                    } else {
                        "匹配 ${hitStat.matchCount} · 生效 ${hitStat.effectiveCount}"
                    }
                    TextButton(text = hitLabel, onClick = { if (hitStat.samples.isNotEmpty()) showSamples = true }, enabled = hitStat.samples.isNotEmpty())
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
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("命中样本 ${hitStat.samples.size} 条", style = MiuixTheme.textStyles.title4)
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
                    TextButton(text = "关闭", onClick = { showSamples = false })
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
