package app.quieta.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Fresh light palette aligned with the selected pastel channel-bar icon.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2A9D8F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB8F0E6),
    onPrimaryContainer = Color(0xFF00332C),
    secondary = Color(0xFFE07A5F),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFAF9F7),
    onBackground = Color(0xFF1A1C1B),
    surface = Color(0xFFFAF9F7),
    onSurface = Color(0xFF1A1C1B),
    surfaceVariant = Color(0xFFF0EEEA),
    onSurfaceVariant = Color(0xFF414944),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FD6C4),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005147),
    onPrimaryContainer = Color(0xFFB8F0E6),
    secondary = Color(0xFFFFB4A2),
    onSecondary = Color(0xFF5C1B0A),
    background = Color(0xFF121412),
    onBackground = Color(0xFFE1E3E0),
    surface = Color(0xFF121412),
    onSurface = Color(0xFFE1E3E0),
    surfaceVariant = Color(0xFF1E221F),
    onSurfaceVariant = Color(0xFFC0C9C2),
)

@Composable
fun QuietaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
