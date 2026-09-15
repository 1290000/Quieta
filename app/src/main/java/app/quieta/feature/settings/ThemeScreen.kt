package app.quieta.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.quieta.core.settings.ThemeMode
import app.quieta.ui.component.QuietaPage

@Composable
fun ThemeScreen(
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    blurEnabled: Boolean,
) {
    QuietaPage(
        title = "主题设置",
        blurEnabled = blurEnabled,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
        },
    ) {
        item(key = "theme-intro") {
            Text(
                "选择息匣的浅色或深色显示方式。跟随系统会使用设备当前的外观设置。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item(key = "theme-options") {
            Column(modifier = Modifier.fillMaxWidth()) {
                ThemeOption("跟随系统", ThemeMode.SYSTEM, mode, onModeChange)
                ThemeOption("浅色", ThemeMode.LIGHT, mode, onModeChange)
                ThemeOption("深色", ThemeMode.DARK, mode, onModeChange)
            }
        }
    }
}

@Composable
private fun ThemeOption(
    title: String,
    value: ThemeMode,
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clickable(onClick = { onSelected(value) }),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected == value, onClick = { onSelected(value) })
        Text(title, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
    }
}
