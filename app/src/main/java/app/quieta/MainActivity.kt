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
            val seedArgb by settings.seedColorInt.collectAsStateWithLifecycle(initialValue = 0xFF6750A4.toInt())
            val colorSpec by settings.themeColorSpec.collectAsStateWithLifecycle(
                initialValue = app.quieta.core.settings.ThemeColorSpec.SPEC_2025,
            )
            val paletteStyle by settings.paletteStyle.collectAsStateWithLifecycle(
                initialValue = app.quieta.core.settings.PaletteStyle.TonalSpot,
            )
            QuietaTheme(
                darkTheme = when (mode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                },
                customColors = customColors,
                dynamicColor = dynamicColor,
                seedColor = Color(seedArgb),
            ) {
                QuietaRoot()
            }
        }
    }
}
