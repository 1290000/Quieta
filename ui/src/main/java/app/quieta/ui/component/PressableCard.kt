package app.quieta.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * InstallerX-style springy press scale (≈0.97 while pressed).
 * Pass the same [interactionSource] into `Modifier.clickable`.
 */
@Composable
fun pressScale(interactionSource: MutableInteractionSource): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pressScale",
    )
    return scale
}

@Composable
fun Modifier.cardPressScale(interactionSource: MutableInteractionSource): Modifier {
    val scale = pressScale(interactionSource)
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
