package app.quieta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.quieta.core.settings.AppSettings
import app.quieta.core.settings.ThemeMode
import app.quieta.nav.QuietaRoot
import app.quieta.ui.theme.QuietaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = AppSettings(applicationContext)
        setContent {
            val mode by settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val customColors by settings.customColors.collectAsStateWithLifecycle(initialValue = false)
            val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
            QuietaTheme(
                darkTheme = when (mode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                },
                customColors = customColors,
                dynamicColor = dynamicColor,
                seedColor = Color(0xFF3482FF),
            ) {
                QuietaRoot()
            }
        }
    }
}
