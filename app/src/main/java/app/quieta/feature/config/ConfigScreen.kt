package app.quieta.feature.config

import android.content.Intent
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quieta.R
import app.quieta.core.model.Rule
import app.quieta.core.model.RuleAction

@Composable
fun ConfigScreen(
    modifier: Modifier = Modifier,
    viewModel: ConfigViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var nameInput by rememberSaveable { mutableStateOf("") }
    var muteSelected by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            // keep snackbar-like message until next change
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.config_title),
                style = MaterialTheme.typography.displaySmall,
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("按名称添加规则", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("渠道名包含…") },
                        placeholder = { Text("例如：推广") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = muteSelected,
                            onClick = { muteSelected = true },
                            label = { Text("静音") },
                        )
                        FilterChip(
                            selected = !muteSelected,
                            onClick = { muteSelected = false },
                            label = { Text("降级") },
                        )
                    }
                    Button(
                        onClick = {
                            val action = if (muteSelected) RuleAction.MUTE else RuleAction.DOWNGRADE
                            viewModel.addNameRule(nameInput, action)
                            nameInput = ""
                        },
                        enabled = nameInput.isNotBlank(),
                    ) {
                        Text("添加")
                    }
                    state.message?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = viewModel::clearMessage) { Text("知道了") }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val json = viewModel.exportJson()
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_TEXT, json)
                        }
                        context.startActivity(Intent.createChooser(send, "导出规则"))
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("导出") }

                OutlinedButton(
                    onClick = {
                        // Simple paste path: user can use adb/scenario later; MVP opens empty import.
                        // Phase 4 uses system picker when SAF file API is wired; for now share target.
                        viewModel.importJson(
                            """{"schemaVersion":1,"rules":[{"id":"demo","enabled":true,"nameContains":"推广","action":"MUTE"}]}""",
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("导入示例") }
            }
        }

        if (state.rules.isEmpty()) {
            item {
                Text(
                    text = "还没有规则。添加后可在主页对匹配渠道批量静音。",
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
    }
}

@Composable
private fun RuleCard(
    rule: Rule,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.nameContains ?: rule.packageName ?: "(全部)",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = when (rule.action) {
                        RuleAction.MUTE -> "静音"
                        RuleAction.DOWNGRADE -> "降级"
                        RuleAction.KEEP -> "保留"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
            TextButton(onClick = onRemove) { Text("删除") }
        }
    }
}
