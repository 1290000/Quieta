package app.quieta.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.quieta.R
import app.quieta.feature.config.ConfigScreen
import app.quieta.feature.home.HomeScreen
import app.quieta.feature.record.RecordScreen
import app.quieta.feature.settings.SettingsScreen
import app.quieta.ui.glass.FloatingBottomBar
import app.quieta.ui.glass.QuietaNavTab
import app.quieta.ui.glass.resolveBottomBarMode

object QuietaRoutes {
    const val HOME = "home"
    const val CONFIG = "config"
    const val RECORD = "record"
    const val SETTINGS = "settings"
}

@Composable
fun QuietaRoot() {
    var selectedRoute by rememberSaveable { mutableStateOf(QuietaRoutes.HOME) }
    var blurEnabled by rememberSaveable { mutableStateOf(true) }

    val tabs = listOf(
        QuietaNavTab(QuietaRoutes.HOME, stringResource(R.string.nav_home), Icons.Outlined.Home),
        QuietaNavTab(QuietaRoutes.CONFIG, stringResource(R.string.nav_config), Icons.AutoMirrored.Outlined.Rule),
        QuietaNavTab(QuietaRoutes.RECORD, stringResource(R.string.nav_record), Icons.Outlined.History),
        QuietaNavTab(QuietaRoutes.SETTINGS, stringResource(R.string.nav_settings), Icons.Outlined.Settings),
    )

    val liquidSupported = android.os.Build.VERSION.SDK_INT >= 33
    val mode = resolveBottomBarMode(
        blurEnabled = blurEnabled,
        liquidGlassSupported = liquidSupported,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        when (selectedRoute) {
            QuietaRoutes.HOME -> HomeScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 96.dp),
            )
            QuietaRoutes.CONFIG -> ConfigScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 96.dp),
            )
            QuietaRoutes.RECORD -> RecordScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 96.dp),
            )
            QuietaRoutes.SETTINGS -> SettingsScreen(
                blurEnabled = blurEnabled,
                onBlurEnabledChange = { blurEnabled = it },
                bottomBarMode = mode,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 96.dp),
            )
        }

        FloatingBottomBar(
            tabs = tabs,
            selectedRoute = selectedRoute,
            onTabSelected = { selectedRoute = it },
            mode = mode,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
