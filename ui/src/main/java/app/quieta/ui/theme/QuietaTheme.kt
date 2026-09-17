package app.quieta.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

// Aligned with InstallerX / miuix defaults (compose-miuix-ui Colors.kt).
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

/** Simple seed-derived scheme when custom colors are on but dynamic color is off. */
private fun seedLightScheme(seed: Color): ColorScheme = lightColorScheme(
    primary = seed,
    onPrimary = Color.White,
    primaryContainer = seed.copy(alpha = 0.18f),
    onPrimaryContainer = Color(0xFF001A41),
    background = Color(0xFFF5F5F6),
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0xFF5C5C5E),
    outlineVariant = Color(0xFFE0E0E0),
    error = Color(0xFFD93025),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFE8E8E8),
    surfaceContainerHighest = Color(0xFFE8E8E8),
)

private fun seedDarkScheme(seed: Color): ColorScheme = darkColorScheme(
    primary = seed.copy(alpha = 0.92f),
    onPrimary = Color(0xFF001A41),
    primaryContainer = seed.copy(alpha = 0.28f),
    onPrimaryContainer = Color(0xFFD6E7FF),
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

/**
 * Theme host. When [customColors] is on:
 * - dynamicColor + API 31+ → system Monet
 * - else → seed-derived scheme from [seedColor]
 */
@Composable
fun QuietaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    customColors: Boolean = false,
    dynamicColor: Boolean = true,
    seedColor: Color = HyperBlue,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        customColors && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        customColors -> {
            if (darkTheme) seedDarkScheme(seedColor) else seedLightScheme(seedColor)
        }
        else -> if (darkTheme) DarkColors else LightColors
    }
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
