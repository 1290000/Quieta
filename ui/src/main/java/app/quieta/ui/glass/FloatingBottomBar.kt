package app.quieta.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop

data class QuietaNavTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * InstallerX-style liquid glass bottom bar.
 * Content is recorded via [layerBackdrop] on the page; this bar samples it with lens refraction.
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

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    val dampedDrag = remember(scope, tabs.size, density) {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..(tabs.size - 1).coerceAtLeast(0).toFloat(),
            canDrag = { pos -> pos.x in 0f..totalWidthPx },
            onDragStarted = { pos ->
                if (tabWidthPx > 0f) {
                    val index = ((pos.x) / tabWidthPx).toInt().coerceIn(0, tabs.lastIndex)
                    updateValue(index.toFloat())
                }
            },
            onDragStopped = {
                val target = targetValue.roundToInt().coerceIn(0, tabs.lastIndex)
                if (tabs.getOrNull(target)?.route != selectedRoute) {
                    onTabSelected(tabs[target].route)
                }
                updateValue(target.toFloat())
            },
            onDragCancelled = {
                updateValue(selectedIndex.toFloat())
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx)
                            .coerceIn(0f, (tabs.size - 1).coerceAtLeast(0).toFloat()),
                    )
                }
            },
        )
    }

    LaunchedEffect(selectedIndex) {
        if (dampedDrag.value != selectedIndex.toFloat()) {
            dampedDrag.animateToValue(selectedIndex.toFloat())
        }
    }

    val activateTab: (Int) -> Unit = remember(dampedDrag) {
        { index: Int ->
            if (index != selectedIndex) onTabSelected(tabs[index].route)
            dampedDrag.animateToValue(index.toFloat())
        }
    }

    val liquid = mode == FloatingBottomBarMode.LiquidGlass
    val blurMode = mode == FloatingBottomBarMode.Blur
    val surfaceAlpha = when {
        liquid -> 0.40f
        blurMode -> 0.65f
        else -> 1f
    }

    Box(
        modifier = modifier
            .width(IntrinsicSize.Min)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Base capsule (glass / blur / solid)
        Box(
            modifier = Modifier
                .height(64.dp)
                .onGloballyPositioned { coords ->
                    totalWidthPx = coords.size.width.toFloat()
                    tabWidthPx = if (tabs.isEmpty()) 0f else totalWidthPx / tabs.size
                }
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
                            .shadow(10.dp, pillShape, clip = false)
                            .clip(pillShape)
                            .background(Color.White.copy(alpha = 0.96f))
                    },
                ),
        ) {
            Row(
                modifier = Modifier
                    .height(64.dp)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = index == selectedIndex
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .width(72.dp)
                            .fillMaxHeight()
                            .semantics { role = Role.Tab }
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = { activateTab(index) },
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

        // Drag / press interaction on the whole bar
        Box(
            modifier = Modifier
                .height(64.dp)
                .then(dampedDrag.modifier),
        )

        // Selection pill
        if (tabWidthPx > 0f && tabs.isNotEmpty()) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            val dx = (dampedDrag.value * tabWidthPx).roundToInt()

            Box(
                modifier = Modifier
                    .offset { IntOffset(dx, 0) }
                    .height(56.dp)
                    .width(tabWidthDp)
                    .then(
                        if (liquid) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { pillShape },
                                effects = {
                                    val p = dampedDrag.pressProgress
                                    lens(
                                        refractionHeight = 10.dp.toPx() * p,
                                        refractionAmount = 14.dp.toPx() * p,
                                        depthEffect = true,
                                        chromaticAberration = 0.5f,
                                    )
                                },
                                onDrawSurface = {
                                    val p = dampedDrag.pressProgress
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.08f),
                                        alpha = 1f - p,
                                    )
                                },
                            ).innerShadow(shape = pillShape) {
                                InnerShadow(
                                    radius = 8.dp * dampedDrag.pressProgress,
                                    color = Color.Black.copy(alpha = 0.15f),
                                    alpha = dampedDrag.pressProgress,
                                )
                            }
                        } else {
                            Modifier
                                .clip(pillShape)
                                .background(Color(0x223482FF))
                                .graphicsLayer {
                                    scaleX = dampedDrag.scaleX
                                    scaleY = dampedDrag.scaleY
                                }
                        },
                    ),
            )

            // Active icon overlay
            Box(
                modifier = Modifier
                    .offset { IntOffset(dx, 0) }
                    .height(56.dp)
                    .width(tabWidthDp),
                contentAlignment = Alignment.Center,
            ) {
                val activeIndex = dampedDrag.value.roundToInt().coerceIn(0, tabs.lastIndex)
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
