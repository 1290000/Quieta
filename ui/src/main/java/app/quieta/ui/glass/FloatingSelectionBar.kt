// SPDX-License-Identifier: GPL-3.0-only
// Bottom action capsule sharing FloatingBottomBar glass chrome (Blur / LiquidGlass / None).
package app.quieta.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.quieta.ui.glass.liquid.lens
import app.quieta.ui.glass.liquid.vibrancy
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource

data class FloatingSelectionAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val emphasized: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Multi-select action capsule. Same glass pipeline as [FloatingBottomBar]:
 * LiquidGlass when shader-safe, Blur otherwise, solid when blur is off.
 */
@Composable
fun FloatingSelectionBar(
    actions: List<FloatingSelectionAction>,
    mode: FloatingBottomBarMode,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    countLabel: String,
    busy: Boolean = false,
) {
    if (actions.isEmpty()) return
    val isInDark = isSystemInDarkTheme()
    val pillShape = remember { CircleShape }
    val isLiquidGlassMode = mode == FloatingBottomBarMode.LiquidGlass
    val isBlurMode = mode == FloatingBottomBarMode.Blur
    val colors = FloatingBottomBarDefaults.colors()
    val containerColor =
        if (isLiquidGlassMode) colors.containerColor.copy(alpha = 0.4f) else colors.containerColor

    val specular = remember {
        Highlight(
            width = 1.dp,
            alpha = 1f,
            style = BloomStroke(
                color = Color.White.copy(alpha = 0.12f),
                innerBlurRadius = 2.0.dp,
                primaryLight = LightSource(
                    position = LightPosition(0.5f, -0.3f, -0.05f),
                    color = Color.White,
                    intensity = 1f,
                ),
                secondaryLight = LightSource(
                    position = LightPosition(0.5f, 0.8f, -0.5f),
                    color = Color.White,
                    intensity = 0.4f,
                ),
                dualPeak = true,
            ),
        )
    }

    Row(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .dropShadow(
                shape = pillShape,
                shadow = Shadow(
                    radius = 10.dp,
                    color = Color.Black,
                    alpha = if (isInDark) 0.2f else 0.1f,
                ),
            )
            .then(
                if (isLiquidGlassMode) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { pillShape },
                        effects = {
                            vibrancy()
                            blur(4.dp.toPx(), 4.dp.toPx())
                            lens(
                                refractionHeight = 24.dp.toPx(),
                                refractionAmount = 24.dp.toPx(),
                            )
                        },
                        highlight = { specular.copy(alpha = 0.75f) },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                } else if (isBlurMode) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { pillShape },
                        effects = { blur(25.dp.toPx(), 25.dp.toPx()) },
                        onDrawSurface = {
                            drawRect(containerColor.copy(alpha = 0.65f))
                        },
                    )
                } else {
                    Modifier
                        .clip(pillShape)
                        .background(containerColor, pillShape)
                },
            )
            .height(64.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (busy) "处理中…" else countLabel,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = colors.contentColor.copy(alpha = 0.75f),
            maxLines = 1,
        )
        Spacer(modifier = Modifier.weight(1f))
        actions.forEach { action ->
            SelectionBarItem(action = action, colors = colors)
        }
    }
}

@Composable
private fun SelectionBarItem(
    action: FloatingSelectionAction,
    colors: FloatingBottomBarColors,
) {
    val tint = when {
        !action.enabled -> colors.contentColor.copy(alpha = 0.35f)
        action.emphasized -> colors.activeContentColor
        else -> colors.contentColor
    }
    Column(
        modifier = Modifier
            .defaultMinSize(minWidth = 64.dp)
            .alpha(if (action.enabled) 1f else 0.55f)
            .then(
                if (action.enabled) {
                    Modifier
                        .clip(CircleShape)
                        .clickable(onClick = action.onClick)
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = tint,
        )
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
        )
    }
}
