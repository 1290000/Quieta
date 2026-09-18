package app.quieta

import android.content.Intent
import android.net.Uri
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
import app.quieta.nav.PendingOpenImport
import app.quieta.nav.QuietaRoot
import app.quieta.service.NotificationListenerAccess
import app.quieta.ui.theme.QuietaTheme
import app.quieta.ui.theme.UiPaletteStyle
import app.quieta.ui.theme.UiThemeColorSpec
import app.quieta.core.settings.PaletteStyle as CorePalette
import app.quieta.core.settings.ThemeColorSpec as CoreSpec

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOpenWith(intent)
        NotificationListenerAccess.ensureBound(this, "activity_create")
        val settings = AppSettings(applicationContext)
        setContent {
            val mode by settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val customColors by settings.customColors.collectAsStateWithLifecycle(initialValue = false)
            val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
            val seedArgb by settings.seedColorInt.collectAsStateWithLifecycle(initialValue = 0xFF6750A4.toInt())
            val coreSpec by settings.themeColorSpec.collectAsStateWithLifecycle(initialValue = CoreSpec.SPEC_2025)
            val corePalette by settings.paletteStyle.collectAsStateWithLifecycle(initialValue = CorePalette.TonalSpot)
            QuietaTheme(
                darkTheme = when (mode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                },
                customColors = customColors,
                dynamicColor = dynamicColor,
                seedColor = Color(seedArgb),
                paletteStyle = when (corePalette) {
                    CorePalette.TonalSpot -> UiPaletteStyle.TonalSpot
                    CorePalette.Vibrant -> UiPaletteStyle.Vibrant
                    CorePalette.Expressive -> UiPaletteStyle.Expressive
                    CorePalette.Spritz -> UiPaletteStyle.Spritz
                    CorePalette.FruitSalad -> UiPaletteStyle.FruitSalad
                    CorePalette.Rainbow -> UiPaletteStyle.Rainbow
                    CorePalette.Monochrome -> UiPaletteStyle.Monochrome
                },
                colorSpec = when (coreSpec) {
                    CoreSpec.SPEC_2021 -> UiThemeColorSpec.SPEC_2021
                    CoreSpec.SPEC_2025 -> UiThemeColorSpec.SPEC_2025
                },
            ) {
                QuietaRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenWith(intent)
        NotificationListenerAccess.ensureBound(this, "activity_new_intent")
    }

    override fun onResume() {
        super.onResume()
        NotificationListenerAccess.ensureBound(this, "activity_resume")
    }

    private fun handleOpenWith(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri: Uri = intent.data ?: return
        PendingOpenImport.offer(uri)
    }
}
