package app.quieta.feature.config

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
    var nameInput by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var packageInput by rememberSaveable { mutableStateOf("") }
    var actionInput by rememberSaveable { mutableStateOf(RuleAction.MUTE) }

    Box(modifier = modifier.fillMaxSize()) {
        QuietaPage(
            title = stringResource(R.string.config_title),
            blurEnabled = blurEnabled,
        ) {
            item {
                InfoBanner(
                    text = "规则按名称匹配通知渠道。第一个命中的启用规则生效。可在主页一键静音。",
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
                    onToggle = { viewModel.toggle(rule.id) },
                    onRemove = { viewModel.remove(rule.id) },
                    onEdit = {
                        editingId = rule.id
                        nameInput = rule.nameContains.orEmpty()
                        packageInput = rule.packageName.orEmpty()
                        actionInput = rule.action
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
                editingId = null
                nameInput = ""
                packageInput = ""
                actionInput = RuleAction.MUTE
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
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(if (editingId == null) R.string.rule_add else R.string.rule_edit),
                    style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("渠道名包含…") },
                    placeholder = { Text("例如：推广") },
                )
                OutlinedTextField(
                    value = packageInput,
                    onValueChange = { packageInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.rule_package)) },
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    RuleAction.entries.forEachIndexed { index, action ->
                        SegmentedButton(
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                activeContentColor = MaterialTheme.colorScheme.primary,
                            ),
                            selected = actionInput == action,
                            onClick = { actionInput = action },
                            shape = SegmentedButtonDefaults.itemShape(index, RuleAction.entries.size),
                        ) { Text(stringResource(when (action) {
                            RuleAction.MUTE -> R.string.rule_mute
                            RuleAction.DOWNGRADE -> R.string.rule_downgrade
                            RuleAction.KEEP -> R.string.rule_keep
                        })) }
                    }
                }
                state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showAdd = false }) { Text("取消") }
                    TextButton(
                        onClick = {
                            viewModel.saveRule(editingId, nameInput, packageInput, actionInput) { showAdd = false }
                        },
                        enabled = nameInput.isNotBlank() || packageInput.isNotBlank() || editingId != null,
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
private fun RuleCard(rule: Rule, onToggle: () -> Unit, onRemove: () -> Unit, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rule.nameContains ?: rule.packageName ?: "(全部)",
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
