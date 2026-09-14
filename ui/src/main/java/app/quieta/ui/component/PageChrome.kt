package app.quieta.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Large page title under status bar (InstallerX / HyperOS style). */
@Composable
fun PageTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontSize = 36.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
    )
}

/** Scrollable page shell with status-bar inset so content never hides under the clock. */
@Composable
fun ScrollablePage(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(bottom = 24.dp),
    ) {
        content()
    }
}

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
