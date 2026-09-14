package app.quieta.ui.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop

data class QuietaNavTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * InstallerX-style floating bar: springy pill, drag follow, press scale, glass refraction.
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
    val scope = rememberCoroutineScope()
    val liquid = mode == FloatingBottomBarMode.LiquidGlass
    val blurMode = mode == FloatingBottomBarMode.Blur

    // Springy position (float index)
    var pos by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var press by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    // Follow external selection with bouncy spring
    val springPos by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "springPos",
    )

    // Use spring when not dragging, raw pos when dragging
    val displayPos = if (dragging) pos else springPos
    if (!dragging && abs(pos - selectedIndex) > 0.01f) {
        pos = selectedIndex.toFloat()
    }

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
            val pressScale = 1f + 0.14f * press

            // Capsule — high contrast white glass
            val capsule = when {
                liquid -> Modifier
                    .shadow(18.dp, pillShape, clip = false)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { pillShape },
                        effects = {
                            vibrancy()
                            blur(8.dp.toPx(), 8.dp.toPx())
                            lens(
                                refractionHeight = 36.dp.toPx(),
                                refractionAmount = 30.dp.toPx(),
                            )
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

            Box(modifier = capsule.matchParentSize())

            // Drag interaction
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(tabs.size, tabPx) {
                        detectDragGestures(
                            onDragStart = {
                                dragging = true
                                scope.launch {
                                    // spring press up
                                    var t = press
                                    while (t < 1f) {
                                        t = (t + 0.12f).coerceAtMost(1f)
                                        press = t
                                        kotlinx.coroutines.delay(16)
                                    }
                                }
                            },
                            onDragEnd = {
                                dragging = false
                                val target = pos.roundToInt().coerceIn(0, tabs.lastIndex)
                                scope.launch {
                                    // spring release press
                                    var t = press
                                    while (t > 0f) {
                                        t = (t - 0.1f).coerceAtLeast(0f)
                                        press = t
                                        kotlinx.coroutines.delay(16)
                                    }
                                }
                                if (tabs[target].route != selectedRoute) onTabSelected(tabs[target].route)
                                pos = target.toFloat()
                            },
                            onDragCancel = {
                                dragging = false
                                pos = selectedIndex.toFloat()
                                press = 0f
                            },
                        ) { change, drag ->
                            change.consume()
                            if (tabPx > 0f) {
                                pos = (pos + drag.x / tabPx)
                                    .coerceIn(0f, (tabs.size - 1).coerceAtLeast(0).toFloat())
                            }
                        }
                    },
            )

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
                                        refractionHeight = 18.dp.toPx() * (0.6f + 0.4f * press),
                                        refractionAmount = 20.dp.toPx() * (0.6f + 0.4f * press),
                                        depthEffect = true,
                                        chromaticAberration = 0.4f + 0.2f * press,
                                    )
                                },
                                onDrawSurface = {
                                    drawRect(Color.White.copy(alpha = 0.38f + 0.15f * press))
                                    drawRect(Color.Black.copy(alpha = 0.07f * press))
                                },
                            ).innerShadow(shape = pillShape) {
                                InnerShadow(
                                    radius = 10.dp * press,
                                    color = Color.Black.copy(alpha = 0.16f),
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

            // Tabs
            Row(
                modifier = Modifier
                    .matchParentSize()
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
                                    if (!dragging) {
                                        pos = index.toFloat()
                                        if (tab.route != selectedRoute) onTabSelected(tab.route)
                                    }
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

            // Active content riding pill
            Box(
                modifier = Modifier
                    .offset { IntOffset(pillX, 0) }
                    .width(tabDp)
                    .height(68.dp)
                    .graphicsLayer {
                        val s = 1f + 0.12f * press
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
