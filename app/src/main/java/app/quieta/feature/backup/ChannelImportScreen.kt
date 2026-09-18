package app.quieta.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quieta.R
import app.quieta.ui.component.QuietaPage
import app.quieta.ui.component.QuietaSwitch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ChannelImportScreen(
    privilegeReady: Boolean,
    privilegeLabel: String,
    onBack: () -> Unit,
    onRequestApply: () -> Unit,
    modifier: Modifier = Modifier,
    blurEnabled: Boolean = true,
    viewModel: ChannelImportViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSkips by remember { mutableStateOf(false) }

    val openDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.previewFromUri(uri)
    }

    QuietaPage(
        title = "导入渠道设置",
        modifier = modifier,
        blurEnabled = blurEnabled,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.navigate_back),
                )
            }
        },
        actions = {
            IconButton(
                onClick = {
                    openDocument.launch(
                        arrayOf("application/json", "text/plain", "text/*", "*/*"),
                    )
                },
            ) {
                Icon(Icons.Outlined.Upload, contentDescription = "选择快照文件")
            }
        },
    ) {
        item(key = "tip") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.primary.copy(alpha = 0.2f)),
            ) {
                Text(
                    text = "选择由息匣导出的渠道快照 JSON。对照本机后仅写入 importance；按包名+渠道 id 匹配，不做名称模糊匹配。系统应用默认不改。",
                    modifier = Modifier.padding(16.dp),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
            }
        }

        item(key = "privilege") {
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = "提权状态",
                    summary = if (privilegeReady) {
                        privilegeLabel + " · 可写入"
                    } else {
                        privilegeLabel + " · 未就绪，仅可预览"
                    },
                )
            }
        }

        item(key = "pick") {
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = "选择快照文件",
                    summary = state.sourceLabel ?: "点击选择 JSON（主页多选导出或设置全量导出）",
                    onClick = {
                        openDocument.launch(
                            arrayOf("application/json", "text/plain", "text/*", "*/*"),
                        )
                    },
                )
            }
        }

        if (state.snapshot != null) {
            item(key = "system-toggle") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = "包含系统应用",
                        summary = "默认关闭；开启后将按快照写入系统应用渠道 importance",
                        endActions = {
                            QuietaSwitch(
                                checked = state.includeSystem,
                                onCheckedChange = viewModel::setIncludeSystem,
                            )
                        },
                    )
                }
            }

            val plan = state.plan
            if (plan != null) {
                item(key = "summary") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "文件：应用 ${plan.fileApps} · 渠道 ${plan.fileChannels}",
                                style = MiuixTheme.textStyles.body2,
                            )
                            Text(
                                text = "将写入 ${plan.applyCount} · 相同 ${plan.sameCount} · 跳过 ${plan.skips.size}",
                                style = MiuixTheme.textStyles.title4,
                                color = MiuixTheme.colorScheme.primary,
                            )
                            Text(
                                text = "未装 ${plan.appMissingCount} App · 无渠道 ${plan.channelMissingCount} · 系统暂缓 ${plan.systemHeldCount}",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            if (plan.applyCount > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    thickness = 0.5.dp,
                                    color = MiuixTheme.colorScheme.dividerLine,
                                )
                                Text(text = "待写入明细（最多 8 条）", style = MiuixTheme.textStyles.footnote2)
                                plan.apply.take(8).forEach { item ->
                                    Text(
                                        text = "${item.appLabel} · ${item.channelName}：${item.currentImportance} → ${item.targetImportance}",
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "skips") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            BasicComponent(
                                title = if (showSkips) "收起跳过项" else "查看跳过项（${plan.skips.size}）",
                                summary = "未安装 / 无渠道 / 系统暂缓",
                                onClick = { showSkips = !showSkips },
                            )
                            if (showSkips) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 280.dp)
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    plan.skips.forEach { skip ->
                                        Text(
                                            text = buildString {
                                                append(skip.appLabel)
                                                skip.channelName?.let { append(" · ").append(it) }
                                                append("：").append(skip.reason)
                                            },
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "apply") {
                    Button(
                        onClick = onRequestApply,
                        enabled = privilegeReady &&
                            !state.applying &&
                            plan.applyCount > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = when {
                                state.applying -> state.progressText ?: "正在应用…"
                                !privilegeReady -> "提权未就绪"
                                plan.applyCount == 0 -> "无待写入项"
                                else -> "应用 ${plan.applyCount} 项 importance"
                            },
                        )
                    }
                }
            }
        }

        state.report?.let { report ->
            item(key = "report") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallTitle("导入结果")
                        Text(
                            text = "目标 ${report.total} · 写入成功 ${report.writeOk} · 写入失败 ${report.writeFailed}",
                            style = MiuixTheme.textStyles.body2,
                        )
                        Text(
                            text = "回读确认 ${report.verified} · 未确认 ${report.unconfirmed} · 不一致 ${report.mismatch}",
                            style = MiuixTheme.textStyles.body2,
                            color = when {
                                report.mismatch > 0 || report.unconfirmed > 0 -> Color(0xFFB45309)
                                else -> MiuixTheme.colorScheme.primary
                            },
                        )
                        if (report.errors.isNotEmpty()) {
                            Text(text = "错误", style = MiuixTheme.textStyles.footnote2)
                            report.errors.forEach {
                                Text(
                                    text = it,
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        if (report.verifyNotes.isNotEmpty()) {
                            Text(text = "回读说明", style = MiuixTheme.textStyles.footnote2)
                            report.verifyNotes.forEach {
                                Text(
                                    text = it,
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }
        }

        state.message?.let { msg ->
            item(key = "message") {
                Text(
                    text = msg,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }

        item(key = "privacy") {
            Text(
                text = "快照仅含包名、渠道 id/importance 等设置元数据，不含通知正文。写入后以系统回读为准。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}
