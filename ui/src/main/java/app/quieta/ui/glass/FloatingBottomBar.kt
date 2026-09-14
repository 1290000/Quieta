package app.quieta.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.quieta.ui.glass.liquid.InnerShadow
import app.quieta.ui.glass.liquid.innerShadow
import app.quieta.ui.glass.liquid.lens
import app.quieta.ui.glass.liquid.vibrancy
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
 * Liquid-glass bottom bar (InstallerX / Kyant0 AndroidLiquidGlass pattern).
 * - Drag the capsule to move the pill with spring follow
 * - Press scales the pill (touch feedback)
 * - LiquidGlass: vibrancy + blur + lens refraction + inner shadow + edge chromatic on pill
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
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val pillShape = remember { CircleShape }
    val selectedIndex = tabs.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)

    val anim = remember { Animatable(selectedIndex.toFloat()) }
    val press = remember { Animatable(0f) }
    val offsetX = remember { Animatable(0f) } // rubber-band residual

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(selectedIndex) {
        if (press.value < 0.01f) {
            anim.animateTo(
                selectedIndex.toFloat(),
                spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f),
            )
        }
    }

    val liquid = mode == FloatingBottomBarMode.LiquidGlass
    val blurMode = mode == FloatingBottomBarMode.Blur
    val surfaceAlpha = when {
        liquid -> 0.40f
        blurMode -> 0.65f
        else -> 1f
    }

    val dragModifier = Modifier.pointerInput(tabs.size) {
        detectDragGestures(
            onDragStart = {
                scope.launch { press.animateTo(1f, spring(1f, 1000f, 0.001f)) }
            },
            onDragEnd = {
                val target = anim.value.roundToInt().coerceIn(0, tabs.lastIndex)
                scope.launch {
                    anim.animateTo(
                        target.toFloat(),
                        spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.8f),
                    )
                    press.animateTo(0f, spring(1f, 1000f, 0.001f))
                    offsetX.animateTo(0f, spring(1f, 300f, 0.5f))
                }
                tabs.getOrNull(target)?.let {
                    if (it.route != selectedRoute) onTabSelected(it.route)
                }
            },
            onDragCancel = {
                scope.launch {
                    anim.animateTo(
                        selectedIndex.toFloat(),
                        spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.8f),
                    )
                    press.animateTo(0f, spring(1f, 1000f, 0.001f))
                    offsetX.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
        ) { change, drag ->
            change.consume()
            if (tabWidthPx > 0f) {
                val next = (anim.value + drag.x / tabWidthPx)
                    .coerceIn(0f, (tabs.size - 1).coerceAtLeast(0).toFloat())
                scope.launch { anim.snapTo(next) }
                scope.launch { offsetX.snapTo(offsetX.value + drag.x * 0.15f) }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .onGloballyPositioned { coords ->
                    totalWidthPx = coords.size.width.toFloat()
                    tabWidthPx = if (tabs.isEmpty()) 0f else {
                        (totalWidthPx - with(density) { 8.dp.toPx() }) / tabs.size
                    }
                }
                .then(dragModifier)
                .then(
                    if (liquid) {
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
                            onDrawSurface = { drawRect(Color.White.copy(alpha = surfaceAlpha)) },
                        )
                    } else if (blurMode) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { pillShape },
                            effects = { blur(25.dp.toPx(), 25.dp.toPx()) },
                            onDrawSurface = { drawRect(Color.White.copy(alpha = surfaceAlpha)) },
                        )
                    } else {
                        Modifier
                            .shadow(8.dp, pillShape, clip = false)
                            .clip(pillShape)
                            .background(Color.White.copy(alpha = surfaceAlpha))
                    },
                ),
        ) {
            RowTabs(
                tabs = tabs,
                selectedIndex = selectedIndex,
                onTabSelected = onTabSelected,
            )
        }

        // Selection pill with lens + press scale
        if (tabWidthPx > 0f && tabs.isNotEmpty()) {
            val dx = (anim.value * tabWidthPx + offsetX.value).roundToInt()
            val scale = 1f + 0.08f * press.value
            val pillW = with(density) { tabWidthPx.toDp() }

            val basePill = Modifier
                .offset { IntOffset(dx, 0) }
                .width(pillW)
                .height(56.dp)
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }

            if (liquid) {
                Box(
                    modifier = basePill.then(
                        Modifier
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { pillShape },
                                effects = {
                                    lens(
                                        refractionHeight = (10.dp.toPx() + 8.dp.toPx() * press.value),
                                        refractionAmount = (14.dp.toPx() + 6.dp.toPx() * press.value),
                                        depthEffect = true,
                                        chromaticAberration = 0.45f,
                                    )
                                },
                                onDrawSurface = {
                                    drawRect(Color.White.copy(alpha = 0.14f + 0.08f * press.value))
                                    drawRect(Color.Black.copy(alpha = 0.04f * press.value))
                                },
                            )
                            .innerShadow(shape = pillShape) {
                                InnerShadow(
                                    radius = 8.dp * press.value,
                                    color = Color.Black.copy(alpha = 0.15f * press.value),
                                    alpha = press.value,
                                )
                            },
                    ),
                )
            } else {
                Box(
                    modifier = basePill.then(
                        Modifier
                            .clip(pillShape)
                            .background(Color(0x223482FF)),
                    ),
                )
            }

            // Active label/icon above pill
            Box(
                modifier = Modifier
                    .offset { IntOffset(dx, 0) }
                    .width(pillW)
                    .height(56.dp)
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        scaleX = 1f + 0.12f * press.value
                        scaleY = 1f + 0.12f * press.value
                    },
                contentAlignment = Alignment.Center,
            ) {
                val activeIndex = anim.value.roundToInt().coerceIn(0, tabs.lastIndex)
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
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowTabs(
    tabs: List<QuietaNavTab>,
    selectedIndex: Int,
    onTabSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            val interaction = remember { MutableInteractionSource() }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics { role = Role.Tab }
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onTabSelected(tab.route) },
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
                    color = if (selected) Color.Transparent else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
