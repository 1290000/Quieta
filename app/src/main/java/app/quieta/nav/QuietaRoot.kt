package app.quieta.nav

import androidx.activity.compose.BackHandler
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
import app.quieta.feature.settings.LicensesScreen
import app.quieta.feature.settings.SettingsScreen
import app.quieta.ui.glass.FloatingBottomBar
import app.quieta.ui.glass.QuietaNavTab
import app.quieta.ui.glass.resolveBottomBarMode
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

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
    var showLicenses by rememberSaveable { mutableStateOf(false) }

    if (showLicenses) {
        BackHandler { showLicenses = false }
        LicensesScreen(onBack = { showLicenses = false })
        return
    }

    val tabs = listOf(
        QuietaNavTab(QuietaRoutes.HOME, stringResource(R.string.nav_home), Icons.Outlined.Home),
        QuietaNavTab(QuietaRoutes.CONFIG, stringResource(R.string.nav_config), Icons.AutoMirrored.Outlined.Rule),
        QuietaNavTab(QuietaRoutes.RECORD, stringResource(R.string.nav_record), Icons.Outlined.History),
        QuietaNavTab(QuietaRoutes.SETTINGS, stringResource(R.string.nav_settings), Icons.Outlined.Settings),
    )

    val liquidSupported = android.os.Build.VERSION.SDK_INT >= 33
    val mode = resolveBottomBarMode(blurEnabled, liquidSupported)
    // Avoid page layerBackdrop: HyperOS RenderThread SIGSEGV with miuix shader.
    val pageBackdrop = rememberLayerBackdrop()

    Box(modifier = Modifier.fillMaxSize()) {
        when (selectedRoute) {
            QuietaRoutes.HOME -> HomeScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp),
            )
            QuietaRoutes.CONFIG -> ConfigScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp),
            )
            QuietaRoutes.RECORD -> RecordScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp),
            )
            QuietaRoutes.SETTINGS -> SettingsScreen(
                blurEnabled = blurEnabled,
                onBlurEnabledChange = { blurEnabled = it },
                bottomBarMode = mode,
                onOpenLicenses = { showLicenses = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp),
            )
        }

        FloatingBottomBar(
            tabs = tabs,
            selectedRoute = selectedRoute,
            onTabSelected = { selectedRoute = it },
            mode = mode,
            backdrop = pageBackdrop,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
