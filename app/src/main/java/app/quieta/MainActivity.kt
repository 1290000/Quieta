package app.quieta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quieta.core.settings.AppSettings
import app.quieta.core.settings.ThemeMode
import app.quieta.nav.QuietaRoot
import app.quieta.ui.theme.QuietaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mode by AppSettings(applicationContext).themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            QuietaTheme(darkTheme = when (mode) {
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }) {
                QuietaRoot()
            }
        }
    }
}
