// SPDX-License-Identifier: GPL-3.0-only
// InstallerX-style aurora backdrop for the About hero page.
package app.quieta.ui.effect

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Full-bleed soft aurora (InstallerX BgEffectBackground static equivalent).
 * Keeps AGSL optional — pure Compose brushes, safe on all API 26+ devices.
 */
@Composable
fun AboutGradientBackground(
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme(),
) {
    val baseColors = if (isDark) {
        listOf(
            Color(0xFF2A2038),
            Color(0xFF3D2A45),
            Color(0xFF2B2848),
            Color(0xFF241E30),
            Color(0xFF1C1824),
        )
    } else {
        listOf(
            Color(0xFFC5B8F0),
            Color(0xFFE8B8D4),
            Color(0xFFF0C8DC),
            Color(0xFFD4C8F5),
            Color(0xFFB8D0F0),
            Color(0xFFF2E4F0),
        )
    }
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = Brush.verticalGradient(colors = baseColors)),
        )
        if (isDark) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFE8B8D4).copy(alpha = 0.18f),
                                Color.Transparent,
                            ),
                            radius = 900f,
                            center = Offset(0.35f, 0.28f),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF8B9CFF).copy(alpha = 0.14f),
                                Color.Transparent,
                            ),
                            radius = 850f,
                            center = Offset(0.82f, 0.12f),
                        ),
                    ),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFD6E8).copy(alpha = 0.55f),
                                Color.Transparent,
                            ),
                            radius = 1100f,
                            center = Offset(0.35f, 0.3f),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFC8D8FF).copy(alpha = 0.45f),
                                Color.Transparent,
                            ),
                            radius = 1000f,
                            center = Offset(0.8f, 0.15f),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFF5E6C8).copy(alpha = 0.28f),
                                Color.Transparent,
                            ),
                            radius = 720f,
                            center = Offset(0.15f, 0.72f),
                        ),
                    ),
            )
        }
    }
}
