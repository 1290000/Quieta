package app.quieta.ui.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.quieta.ui.glass.liquid.InnerShadow
import app.quieta.ui.glass.liquid.innerShadow
import app.quieta.ui.glass.liquid.lens
import app.quieta.ui.glass.liquid.vibrancy
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop

data class QuietaNavTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * Floating bottom bar.
 * Drag is on the capsule parent so tab clickable does not steal the gesture.
 */
@Composable
fun FloatingBottomBar(
    tabs: List<QuietaNavTab>,
    selectedRoute: String,
    onTabSelected: (String) -> Unit,
    mode: FloatingBottomBarMode,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return
    val selectedIndex = tabs.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
    val pillShape = remember { CircleShape }
    val liquid = mode == FloatingBottomBarMode.LiquidGlass
    val blurMode = mode == FloatingBottomBarMode.Blur

    var dragPos by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    var press by remember { mutableFloatStateOf(0f) }

    // Spring follow when not dragging
    val springPos by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pill",
    )
    val displayPos = if (isDragging) dragPos else springPos
    val pressScale = 1f + 0.16f * press

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
        ) {
            val density = LocalDensity.current
            val tabPx = constraints.maxWidth.toFloat() / tabs.size
            val tabDp = with(density) { tabPx.toDp() }
            val pillX = (displayPos * tabPx).roundToInt()

            val capsuleModifier = when {
                liquid -> Modifier
                    .shadow(18.dp, pillShape, clip = false)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { pillShape },
                        effects = {
                            vibrancy()
                            blur(8.dp.toPx(), 8.dp.toPx())
                            lens(refractionHeight = 36.dp.toPx(), refractionAmount = 30.dp.toPx())
                        },
                        onDrawSurface = { drawRect(Color.White.copy(alpha = 0.82f)) },
                    )
                blurMode -> Modifier
                    .shadow(18.dp, pillShape, clip = false)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { pillShape },
                        effects = { blur(30.dp.toPx(), 30.dp.toPx()) },
                        onDrawSurface = { drawRect(Color.White.copy(alpha = 0.9f)) },
                    )
                else -> Modifier
                    .shadow(18.dp, pillShape, clip = false)
                    .clip(pillShape)
                    .background(Color.White.copy(alpha = 0.97f))
            }

            // Capsule + drag on PARENT with Initial pass so tabs' clickable don't steal it
            Box(
                modifier = capsuleModifier
                    .matchParentSize()
                    .pointerInput(tabs.size, tabPx) {
                        awaitEachGesture {
                            val initialDown = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial,
                            )
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isDragging = true
                            press = 1f
                            dragPos = displayPos
                            // Track drag until up
                            var pointerId = initialDown.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                if (change.changedToUpIgnoreConsumed()) {
                                    isDragging = false
                                    press = 0f
                                    val target = dragPos.roundToInt().coerceIn(0, tabs.lastIndex)
                                    if (tabs[target].route != selectedRoute) {
                                        onTabSelected(tabs[target].route)
                                    }
                                    break
                                }
                                val delta = change.position - change.previousPosition
                                if (tabPx > 0f && (delta.x != 0f || delta.y != 0f)) {
                                    dragPos = (dragPos + delta.x / tabPx)
                                        .coerceIn(0f, (tabs.size - 1).coerceAtLeast(0).toFloat())
                                    change.consume()
                                }
                                pointerId = change.id
                            }
                        }
                    },
            ) {
                // Tabs inside capsule
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val selected = index == selectedIndex
                        val interaction = remember { MutableInteractionSource() }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .width(tabDp)
                                .fillMaxHeight()
                                .semantics { role = Role.Tab }
                                .clickable(
                                    interactionSource = interaction,
                                    indication = null,
                                    onClick = {
                                        dragPos = index.toFloat()
                                        if (tab.route != selectedRoute) onTabSelected(tab.route)
                                    },
                                ),
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (selected) Color.Transparent else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) Color.Transparent else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            // Pill
            Box(
                modifier = Modifier
                    .offset { IntOffset(pillX, with(density) { 6.dp.toPx().roundToInt() }) }
                    .width(tabDp)
                    .height(56.dp)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .then(
                        if (liquid) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { pillShape },
                                effects = {
                                    lens(
                                        refractionHeight = 18.dp.toPx(),
                                        refractionAmount = 20.dp.toPx(),
                                        depthEffect = true,
                                        chromaticAberration = 0.5f,
                                    )
                                },
                                onDrawSurface = {
                                    drawRect(Color.White.copy(alpha = 0.38f))
                                    drawRect(Color.Black.copy(alpha = 0.06f * press))
                                },
                            ).innerShadow(shape = pillShape) {
                                InnerShadow(
                                    radius = 10.dp * press,
                                    color = Color.Black.copy(alpha = 0.15f),
                                    alpha = press,
                                )
                            }
                        } else {
                            Modifier
                                .clip(pillShape)
                                .background(Color(0xFFEDEDED))
                        },
                    ),
            )

            // Active icon riding pill
            Box(
                modifier = Modifier
                    .offset { IntOffset(pillX, 0) }
                    .width(tabDp)
                    .height(68.dp)
                    .graphicsLayer {
                        val s = 1f + 0.1f * press
                        scaleX = s
                        scaleY = s
                    },
                contentAlignment = Alignment.Center,
            ) {
                val activeIndex = displayPos.roundToInt().coerceIn(0, tabs.lastIndex)
                val tab = tabs[activeIndex]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
