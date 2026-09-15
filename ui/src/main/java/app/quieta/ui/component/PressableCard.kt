// SPDX-License-Identifier: GPL-3.0-only
// Adapted from InstallerX Revived MiuixHomePage (2026 contributors).
package app.quieta.ui.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.squircle.isSquircleEnabled
import top.yukonga.miuix.kmp.utils.TiltFeedback
import top.yukonga.miuix.kmp.utils.pressable

/** Position-aware tilt and press tint shared by actionable standalone cards. */
@Composable
fun PressableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    cornerRadius: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val feedback = remember { TiltFeedback() }
    val squircle = isSquircleEnabled()
    val shape = remember(cornerRadius, squircle) { CardOutline(cornerRadius, squircle) }
    val animatedColor = animateColorAsState(color, label = "cardBackground")
    CompositionLocalProvider(
        top.yukonga.miuix.kmp.theme.LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
        modifier = modifier
            .pressable(interactionSource = interactionSource, indication = feedback, delay = null)
            .clip(shape)
            .drawBehind { drawRect(animatedColor.value) }
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick),
        content = content,
        )
    }
}

/** Same miuix silhouette, without decoding a shader texture on the first draw. */
private class CardOutline(private val radius: Dp, private val squircle: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply {
            addSquircleRect(size.width, size.height, with(density) { radius.toPx() }, squircleEnabled = squircle)
        })
}
