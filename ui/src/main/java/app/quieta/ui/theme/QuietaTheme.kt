package app.quieta.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// HyperOS / InstallerX-like: neutral surfaces, system blue accent.
// Green is reserved for the running-status card only (see HomeScreen).
private val HyperBlue = Color(0xFF3482FF)
private val HyperOnBlue = Color(0xFFFFFFFF)
private val HyperBgLight = Color(0xFFF5F5F6)
private val HyperSurfaceLight = Color(0xFFFFFFFF)
private val HyperBgDark = Color(0xFF121212)
private val HyperSurfaceDark = Color(0xFF1C1C1E)

private val LightColors = lightColorScheme(
    primary = HyperBlue,
    onPrimary = HyperOnBlue,
    primaryContainer = Color(0xFFE8F0FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF5B616B),
    onSecondary = Color.White,
    background = HyperBgLight,
    onBackground = Color(0xFF111111),
    surface = HyperSurfaceLight,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF0F0F1),
    onSurfaceVariant = Color(0xFF5C5C5E),
    outlineVariant = Color(0xFFE5E5E6),
    error = Color(0xFFD93025),
    surfaceContainer = Color(0xFFF0F0F1),
    surfaceContainerHigh = Color(0xFFE8E8EA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7EB0FF),
    onPrimary = Color(0xFF00306E),
    primaryContainer = Color(0xFF1A3A6B),
    onPrimaryContainer = Color(0xFFD6E7FF),
    secondary = Color(0xFFA0A6B0),
    onSecondary = Color(0xFF1A1C1E),
    background = HyperBgDark,
    onBackground = Color(0xFFEDEDED),
    surface = HyperSurfaceDark,
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF2A2A2C),
    onSurfaceVariant = Color(0xFFAEAEB2),
    outlineVariant = Color(0xFF3A3A3C),
    error = Color(0xFFF28B82),
    surfaceContainer = Color(0xFF2A2A2C),
    surfaceContainerHigh = Color(0xFF323234),
)

object QuietaColors {
    val Accent = HyperBlue
}

@Composable
fun QuietaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = QuietaTypography,
        content = content,
    )
}
