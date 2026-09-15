package app.quieta.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

// Aligned with InstallerX / miuix defaults (compose-miuix-ui Colors.kt).
// Light surfaceContainer = White; dark surfaceContainer = #242424.
private val HyperBlue = Color(0xFF3482FF)

private val LightColors = lightColorScheme(
    primary = HyperBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF5B616B),
    onSecondary = Color.White,
    background = Color(0xFFF5F5F6),
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0xFF5C5C5E),
    outlineVariant = Color(0xFFE0E0E0),
    error = Color(0xFFD93025),
    // miuix light
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFE8E8E8),
    surfaceContainerHighest = Color(0xFFE8E8E8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7EB0FF),
    onPrimary = Color(0xFF00306E),
    primaryContainer = Color(0xFF1A3A6B),
    onPrimaryContainer = Color(0xFFD6E7FF),
    secondary = Color(0xFFA0A6B0),
    onSecondary = Color(0xFF1A1C1E),
    // miuix dark
    background = Color(0xFF242424),
    onBackground = Color(0xE6FFFFFF),
    surface = Color(0xFF2C2C2C),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFFAEAEB2),
    outlineVariant = Color(0xFF404040),
    error = Color(0xFFF28B82),
    surfaceContainer = Color(0xFF242424),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2D2D2D),
)

object QuietaColors {
    val Accent = HyperBlue
    val StatusGreenDark = Color(0xFF163D25)
}

@Composable
fun QuietaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val miuixColors = (if (darkTheme) miuixDarkColorScheme() else miuixLightColorScheme()).copy(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        background = colors.background,
        onBackground = colors.onBackground,
        surface = colors.background,
        onSurface = colors.onSurface,
        surfaceContainer = colors.surface,
        onSurfaceContainer = colors.onSurface,
        onSurfaceVariantSummary = colors.onSurfaceVariant,
    )
    MaterialTheme(
        colorScheme = colors,
        typography = QuietaTypography,
    ) {
        MiuixTheme(colors = miuixColors, content = content)
    }
}
