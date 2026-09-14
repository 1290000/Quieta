package app.quieta.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class QuietaNavTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * Pill bottom bar. Phase 1 draws solid / translucent stand-ins;
 * real LiquidGlass shader lands in a later phase (see AGENTS.md §6).
 */
@Composable
fun FloatingBottomBar(
    tabs: List<QuietaNavTab>,
    selectedRoute: String,
    onTabSelected: (String) -> Unit,
    mode: FloatingBottomBarMode,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (mode) {
        FloatingBottomBarMode.LiquidGlass -> Color.White.copy(alpha = 0.72f)
        FloatingBottomBarMode.Blur -> Color.White.copy(alpha = 0.88f)
        FloatingBottomBarMode.None -> MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(containerColor)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tabs.forEach { tab ->
                NavigationBarItem(
                    selected = tab.route == selectedRoute,
                    onClick = { onTabSelected(tab.route) },
                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                    label = { Text(tab.label) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }
        }
    }
}
