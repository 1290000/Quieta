// SPDX-License-Identifier: GPL-3.0-only
// Theme aligned with InstallerX InstallerTheme + ThemeController (miuix) + materialkolor (M3).
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
import androidx.compose.ui.res.colorResource
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFruitSalad
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeRainbow
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant
import com.materialkolor.dynamiccolor.ColorSpec
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec as MiuixColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle as MiuixPaletteStyle
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

private val HyperBlue = Color(0xFF3482FF)

/** UI-layer mirrors of core settings (ui module must not depend on core). */
enum class UiPaletteStyle {
    TonalSpot, Vibrant, Expressive, Spritz, FruitSalad, Rainbow, Monochrome
}

enum class UiThemeColorSpec { SPEC_2021, SPEC_2025 }

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

/** Full-page seed scheme — InstallerX materialkolor output, including surface/background. */
fun seedMaterialScheme(
    seed: Color,
    dark: Boolean,
    style: UiPaletteStyle,
    spec: UiThemeColorSpec,
): ColorScheme {
    val hct = Hct.fromInt(seed.toArgb())
    val specVersion = when (spec) {
        UiThemeColorSpec.SPEC_2021 -> ColorSpec.SpecVersion.SPEC_2021
        UiThemeColorSpec.SPEC_2025 -> {
            val supported = when (style) {
                UiPaletteStyle.TonalSpot,
                UiPaletteStyle.Vibrant,
                UiPaletteStyle.Expressive,
                UiPaletteStyle.Rainbow,
                -> true
                else -> false
            }
            if (supported) ColorSpec.SpecVersion.SPEC_2025 else ColorSpec.SpecVersion.SPEC_2021
        }
    }
    val scheme: DynamicScheme = when (style) {
        UiPaletteStyle.TonalSpot -> SchemeTonalSpot(hct, dark, 0.0, specVersion)
        UiPaletteStyle.Vibrant -> SchemeVibrant(hct, dark, 0.0, specVersion)
        UiPaletteStyle.Expressive -> SchemeExpressive(hct, dark, 0.0, specVersion)
        UiPaletteStyle.Spritz -> SchemeNeutral(hct, dark, 0.0, specVersion)
        UiPaletteStyle.FruitSalad -> SchemeFruitSalad(hct, dark, 0.0, specVersion)
        UiPaletteStyle.Rainbow -> SchemeRainbow(hct, dark, 0.0, specVersion)
        UiPaletteStyle.Monochrome -> SchemeMonochrome(hct, dark, 0.0, specVersion)
    }
    val base = if (dark) darkColorScheme() else lightColorScheme()
    // Map ALL visible roles from seed so custom colors cover the whole page.
    return base.copy(
        primary = Color(scheme.primary),
        onPrimary = Color(scheme.onPrimary),
        primaryContainer = Color(scheme.primaryContainer),
        onPrimaryContainer = Color(scheme.onPrimaryContainer),
        secondary = Color(scheme.secondary),
        onSecondary = Color(scheme.onSecondary),
        secondaryContainer = Color(scheme.secondaryContainer),
        onSecondaryContainer = Color(scheme.onSecondaryContainer),
        tertiary = Color(scheme.tertiary),
        onTertiary = Color(scheme.onTertiary),
        tertiaryContainer = Color(scheme.tertiaryContainer),
        onTertiaryContainer = Color(scheme.onTertiaryContainer),
        inversePrimary = Color(scheme.inversePrimary),
        background = Color(scheme.background),
        onBackground = Color(scheme.onBackground),
        surface = Color(scheme.surface),
        onSurface = Color(scheme.onSurface),
        surfaceVariant = Color(scheme.surfaceVariant),
        onSurfaceVariant = Color(scheme.onSurfaceVariant),
        outline = Color(scheme.outline),
        outlineVariant = Color(scheme.outlineVariant),
        error = Color(scheme.error),
        onError = Color(scheme.onError),
        errorContainer = Color(scheme.errorContainer),
        onErrorContainer = Color(scheme.onErrorContainer),
        inverseSurface = Color(scheme.inverseSurface),
        inverseOnSurface = Color(scheme.inverseOnSurface),
        surfaceContainer = Color(scheme.surfaceContainer),
        surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
        surfaceContainerHighest = Color(scheme.surfaceContainerHighest),
        surfaceContainerLow = Color(scheme.surfaceContainerLow),
        surfaceContainerLowest = Color(scheme.surfaceContainerLowest),
        surfaceBright = Color(scheme.surfaceBright),
        surfaceDim = Color(scheme.surfaceDim),
        scrim = Color(scheme.scrim),
    )
}

private fun mapPalette(style: UiPaletteStyle): MiuixPaletteStyle = when (style) {
    UiPaletteStyle.TonalSpot -> MiuixPaletteStyle.TonalSpot
    UiPaletteStyle.Vibrant -> MiuixPaletteStyle.Vibrant
    UiPaletteStyle.Expressive -> MiuixPaletteStyle.Expressive
    UiPaletteStyle.Spritz -> MiuixPaletteStyle.Neutral
    UiPaletteStyle.FruitSalad -> MiuixPaletteStyle.FruitSalad
    UiPaletteStyle.Rainbow -> MiuixPaletteStyle.Rainbow
    UiPaletteStyle.Monochrome -> MiuixPaletteStyle.Monochrome
}

private fun mapSpec(style: UiPaletteStyle, spec: UiThemeColorSpec): MiuixColorSpec = when (spec) {
    UiThemeColorSpec.SPEC_2021 -> MiuixColorSpec.Spec2021
    UiThemeColorSpec.SPEC_2025 -> {
        val supported = when (style) {
            UiPaletteStyle.TonalSpot,
            UiPaletteStyle.Vibrant,
            UiPaletteStyle.Expressive,
            UiPaletteStyle.Rainbow,
            -> true
            else -> false
        }
        if (supported) MiuixColorSpec.Spec2025 else MiuixColorSpec.Spec2021
    }
}

/**
 * Quieta theme — InstallerX InstallerTheme pattern:
 * customColors on → seed/dynamic scheme drives Material3 + miuix ThemeController together.
 */
@Composable
fun QuietaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    customColors: Boolean = false,
    dynamicColor: Boolean = true,
    seedColor: Color = HyperBlue,
    paletteStyle: UiPaletteStyle = UiPaletteStyle.TonalSpot,
    colorSpec: UiThemeColorSpec = UiThemeColorSpec.SPEC_2025,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val keyColor = when {
        customColors && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            colorResource(id = android.R.color.system_accent1_500)
        customColors -> seedColor
        else -> HyperBlue
    }

    val colors = when {
        customColors && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        customColors -> seedMaterialScheme(keyColor, darkTheme, paletteStyle, colorSpec)
        else -> if (darkTheme) DarkColors else LightColors
    }

    val controller = if (customColors) {
        ThemeController(
            colorSchemeMode = if (darkTheme) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
            keyColor = keyColor,
            paletteStyle = mapPalette(paletteStyle),
            colorSpec = mapSpec(paletteStyle, colorSpec),
            isDark = darkTheme,
        )
    } else {
        ThemeController(
            colorSchemeMode = if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light,
            lightColors = miuixLightColorScheme().copy(
                primary = LightColors.primary,
                onPrimary = LightColors.onPrimary,
                background = LightColors.background,
                onBackground = LightColors.onBackground,
                surface = LightColors.background,
                onSurface = LightColors.onSurface,
                surfaceContainer = LightColors.surface,
                onSurfaceContainer = LightColors.onSurface,
            ),
            darkColors = miuixDarkColorScheme().copy(
                primary = DarkColors.primary,
                onPrimary = DarkColors.onPrimary,
                background = DarkColors.background,
                onBackground = DarkColors.onBackground,
                surface = DarkColors.surface,
                onSurface = DarkColors.onSurface,
                surfaceContainer = DarkColors.surfaceContainer,
                onSurfaceContainer = DarkColors.onSurface,
            ),
            isDark = darkTheme,
        )
    }

    MaterialTheme(colorScheme = colors, typography = QuietaTypography) {
        MiuixTheme(controller = controller, content = content)
    }
}
