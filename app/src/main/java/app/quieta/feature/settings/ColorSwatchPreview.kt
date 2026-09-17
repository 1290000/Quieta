// SPDX-License-Identifier: GPL-3.0-only
// Swatch preview adapted from InstallerX ColorPalatteCard ColorSwatchPreview.
package app.quieta.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.quieta.core.settings.PaletteStyle
import app.quieta.core.settings.ThemeColorSpec
import app.quieta.ui.theme.RawColor
import app.quieta.ui.theme.presetColorName
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFruitSalad
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeRainbow
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val swatchSchemeCache = ConcurrentHashMap<String, ColorScheme>()

fun materialkolorScheme(
    seed: Color,
    dark: Boolean,
    style: PaletteStyle,
    colorSpec: ThemeColorSpec,
): ColorScheme {
    val hct = Hct.fromInt(seed.toArgb())
    val spec = when (colorSpec) {
        ThemeColorSpec.SPEC_2025 -> {
            val supported = when (style) {
                PaletteStyle.TonalSpot,
                PaletteStyle.Vibrant,
                PaletteStyle.Expressive,
                PaletteStyle.Rainbow,
                -> true
                else -> false
            }
            if (supported) ColorSpec.SpecVersion.SPEC_2025 else ColorSpec.SpecVersion.SPEC_2021
        }
        ThemeColorSpec.SPEC_2021 -> ColorSpec.SpecVersion.SPEC_2021
    }
    val scheme: DynamicScheme = when (style) {
        PaletteStyle.TonalSpot -> SchemeTonalSpot(hct, dark, 0.0, spec)
        PaletteStyle.Vibrant -> SchemeVibrant(hct, dark, 0.0, spec)
        PaletteStyle.Expressive -> SchemeExpressive(hct, dark, 0.0, spec)
        PaletteStyle.Spritz -> SchemeNeutral(hct, dark, 0.0, spec)
        PaletteStyle.FruitSalad -> SchemeFruitSalad(hct, dark, 0.0, spec)
        PaletteStyle.Rainbow -> SchemeRainbow(hct, dark, 0.0, spec)
        PaletteStyle.Monochrome -> SchemeMonochrome(hct, dark, 0.0, spec)
    }
    return if (dark) {
        darkColorScheme(
            primary = Color(scheme.primary),
            onPrimary = Color(scheme.onPrimary),
            primaryContainer = Color(scheme.primaryContainer),
            secondary = Color(scheme.secondary),
            secondaryContainer = Color(scheme.secondaryContainer),
            tertiary = Color(scheme.tertiary),
            tertiaryContainer = Color(scheme.tertiaryContainer),
            inversePrimary = Color(scheme.inversePrimary),
        )
    } else {
        lightColorScheme(
            primary = Color(scheme.primary),
            onPrimary = Color(scheme.onPrimary),
            primaryContainer = Color(scheme.primaryContainer),
            secondary = Color(scheme.secondary),
            secondaryContainer = Color(scheme.secondaryContainer),
            tertiary = Color(scheme.tertiary),
            tertiaryContainer = Color(scheme.tertiaryContainer),
            inversePrimary = Color(scheme.inversePrimary),
        )
    }
}

@Composable
fun ColorSwatchPreview(
    rawColor: RawColor,
    paletteStyle: PaletteStyle,
    colorSpec: ThemeColorSpec,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val cacheKey = remember(rawColor.color.toArgb(), paletteStyle, colorSpec) {
        "${rawColor.color.toArgb()}_${paletteStyle.name}_${colorSpec.name}_light"
    }
    val scheme by produceState<ColorScheme?>(
        initialValue = swatchSchemeCache[cacheKey],
        key1 = cacheKey,
    ) {
        val cached = swatchSchemeCache[cacheKey]
        if (cached != null) {
            value = cached
        } else {
            withContext(Dispatchers.Default) {
                val generated = materialkolorScheme(
                    seed = rawColor.color,
                    dark = false,
                    style = paletteStyle,
                    colorSpec = colorSpec,
                )
                swatchSchemeCache[cacheKey] = generated
                value = generated
            }
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        val current = scheme
        if (current != null) {
            val primaryForSwatch = current.primaryContainer.copy(alpha = 0.9f)
            val secondaryForSwatch = current.secondaryContainer.copy(alpha = 0.6f)
            val tertiaryForSwatch = current.tertiaryContainer.copy(alpha = 0.9f)
            val squircleBg = current.primary.copy(alpha = 0.3f)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(color = squircleBg, shape = RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(color = primaryForSwatch, startAngle = 180f, sweepAngle = 180f, useCenter = true)
                        drawArc(color = tertiaryForSwatch, startAngle = 90f, sweepAngle = 90f, useCenter = true)
                        drawArc(color = secondaryForSwatch, startAngle = 0f, sweepAngle = 90f, useCenter = true)
                    }
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(current.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = current.inversePrimary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(rawColor.color.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(rawColor.color.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.size(26.dp).clip(CircleShape).background(rawColor.color),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = presetColorName(rawColor.key),
            style = MiuixTheme.textStyles.footnote1,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
