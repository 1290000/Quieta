package app.quieta.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** InstallerX-like switch: light gray track when off, system blue when on. */
@Composable
fun QuietaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = installerLikeSwitchColors(),
    )
}

@Composable
fun installerLikeSwitchColors(): SwitchColors {
    return SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        checkedBorderColor = Color.Transparent,
        checkedIconColor = MaterialTheme.colorScheme.primary,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = Color(0xFFE5E5E7),
        uncheckedBorderColor = Color.Transparent,
        uncheckedIconColor = Color.Transparent,
        disabledCheckedThumbColor = Color.White.copy(alpha = 0.8f),
        disabledCheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        disabledUncheckedThumbColor = Color.White.copy(alpha = 0.8f),
        disabledUncheckedTrackColor = Color(0xFFF0F0F1),
    )
}
