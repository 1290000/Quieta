package app.quieta.feature.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    modifier: Modifier = Modifier,
    viewModel: ConfigViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var nameInput by rememberSaveable { mutableStateOf("") }
    var muteSelected by rememberSaveable { mutableStateOf(true) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                app.quieta.ui.component.PageTitle(stringResource(R.string.config_title))
            }

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
                )
            }

            state.message?.let { msg ->
                item {
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true },
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
        ModalBottomSheet(onDismissRequest = { showAdd = false }) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("添加规则", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("渠道名包含…") },
                    placeholder = { Text("例如：推广") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill(selected = muteSelected, label = "静音", onClick = { muteSelected = true })
                    FilterPill(selected = !muteSelected, label = "降级", onClick = { muteSelected = false })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showAdd = false }) { Text("取消") }
                    TextButton(
                        onClick = {
                            val action = if (muteSelected) RuleAction.MUTE else RuleAction.DOWNGRADE
                            viewModel.addNameRule(nameInput, action)
                            nameInput = ""
                            showAdd = false
                        },
                        enabled = nameInput.isNotBlank(),
                    ) { Text("确定") }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(selected: Boolean, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = if (selected) "● $label" else label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoBanner(text: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F0FE)),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF1A56A8),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = null, tint = Color(0xFF1A56A8))
            }
        }
    }
}

@Composable
private fun RuleCard(rule: Rule, onToggle: () -> Unit, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rule.nameContains ?: rule.packageName ?: "(全部)",
                    style = MaterialTheme.typography.titleLarge,
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
            .background(Color(0xFFE8F0FE), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color(0xFF1A56A8))
    }
}
