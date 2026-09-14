package app.quieta.ui.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
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
 * HyperOS / InstallerX-style floating bar.
 * - Solid glass-like capsule (or miuix lens when shader enabled)
 * - Animated selection pill that always tracks selectedIndex
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

    // Always animate pill to selected index (fixes stuck/wrong-tab bug).
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f),
        label = "navPill",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            val density = LocalDensity.current
            val tabWidthPx = constraints.maxWidth / tabs.size
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            val pillOffsetX = (animatedIndex * tabWidthPx).roundToInt()
            val pillOffsetY = with(density) { 4.dp.toPx().roundToInt() }

            // Capsule
            val capsuleModifier = if (liquid) {
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { pillShape },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx(), 4.dp.toPx())
                        lens(
                            refractionHeight = 28.dp.toPx(),
                            refractionAmount = 26.dp.toPx(),
                        )
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 0.42f))
                    },
                )
            } else if (blurMode) {
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { pillShape },
                    effects = { blur(25.dp.toPx(), 25.dp.toPx()) },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 0.72f))
                    },
                )
            } else {
                Modifier
                    .shadow(12.dp, pillShape, clip = false)
                    .clip(pillShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.98f), Color.White.copy(alpha = 0.92f)),
                        ),
                    )
            }

            Box(modifier = capsuleModifier.matchParentSize())

            // Selection pill (always tracks selectedIndex)
            Box(
                modifier = Modifier
                    .offset { IntOffset(pillOffsetX, pillOffsetY) }
                    .width(tabWidthDp)
                    .height(56.dp)
                    .then(
                        if (liquid) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { pillShape },
                                effects = {
                                    lens(
                                        refractionHeight = 14.dp.toPx(),
                                        refractionAmount = 16.dp.toPx(),
                                        depthEffect = true,
                                        chromaticAberration = 0.45f,
                                    )
                                },
                                onDrawSurface = {
                                    drawRect(Color.White.copy(alpha = 0.22f))
                                    drawRect(Color.Black.copy(alpha = 0.04f))
                                },
                            ).innerShadow(shape = pillShape) {
                                InnerShadow(
                                    radius = 8.dp,
                                    color = Color.Black.copy(alpha = 0.12f),
                                )
                            }
                        } else {
                            Modifier
                                .clip(pillShape)
                                .background(Color(0x283482FF))
                        },
                    ),
            )

            // Tabs
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = index == selectedIndex
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .width(tabWidthDp)
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

            // Active tab content above pill (always visible, no transparent hole)
            Box(
                modifier = Modifier
                    .offset { IntOffset(pillOffsetX, 0) }
                    .width(tabWidthDp)
                    .height(64.dp),
                contentAlignment = Alignment.Center,
            ) {
                val tab = tabs[selectedIndex]
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
