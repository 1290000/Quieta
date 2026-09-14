package app.quieta.feature.settings

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quieta.BuildConfig
import app.quieta.R
import app.quieta.service.QuietaNotificationListener
import app.quieta.ui.glass.FloatingBottomBarMode

@Composable
fun SettingsScreen(
    blurEnabled: Boolean,
    onBlurEnabledChange: (Boolean) -> Unit,
    bottomBarMode: FloatingBottomBarMode,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
    aboutViewModel: AboutViewModel = viewModel(),
) {
    val context = LocalContext.current
    val autoMute by viewModel.autoMuteNewChannels.collectAsStateWithLifecycle()
    val updateState by aboutViewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(top = 24.dp, bottom = 16.dp),
        )

        SettingsCard(title = "治理") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("新渠道自动静音")
                    Text(
                        text = "默认关闭。开启后需「通知使用权」，且 Shizuku 可用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = autoMute,
                    onCheckedChange = { viewModel.setAutoMuteNewChannels(it) },
                )
            }
            AboutRow(
                title = "通知使用权",
                subtitle = "打开系统设置，允许息匣读取通知",
            ) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                    )
                }
            }
        }

        SettingsCard(title = stringResource(R.string.settings_appearance)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_bottom_bar_mode))
                    Text(
                        text = modeLabel(bottomBarMode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = blurEnabled, onCheckedChange = onBlurEnabledChange)
            }
        }

        SettingsCard(title = stringResource(R.string.settings_about)) {
            AboutRow(
                title = stringResource(R.string.about_source),
                subtitle = stringResource(R.string.about_source_desc),
            ) {
                openUrl(context, context.getString(R.string.repo_url))
            }
            AboutRow(
                title = stringResource(R.string.about_licenses),
                subtitle = stringResource(R.string.about_licenses_desc),
            ) {
                onOpenLicenses()
            }
            AboutRow(
                title = stringResource(R.string.about_check_update),
                subtitle = updateState.message ?: stringResource(R.string.about_check_update_desc),
            ) {
                aboutViewModel.checkUpdate()
            }
            updateState.releaseUrl?.let { url ->
                AboutRow(title = "打开 Release 页", subtitle = url) {
                    openUrl(context, url)
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 0.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.about_version) + " · " + BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.about_author) + " · " + stringResource(R.string.about_author_name),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "组件 " + ComponentName(context, QuietaNotificationListener::class.java).flattenToString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun modeLabel(mode: FloatingBottomBarMode): String = when (mode) {
    FloatingBottomBarMode.LiquidGlass -> stringResource(R.string.settings_mode_liquid)
    FloatingBottomBarMode.Blur -> stringResource(R.string.settings_mode_blur)
    FloatingBottomBarMode.None -> stringResource(R.string.settings_mode_none)
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun AboutRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
