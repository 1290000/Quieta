package app.quieta.nav

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quieta.R
import app.quieta.feature.config.ConfigScreen
import app.quieta.feature.home.HomeScreen
import app.quieta.feature.home.HomeViewModel
import app.quieta.feature.privilege.PrivilegeScreen
import app.quieta.feature.record.RecordScreen
import app.quieta.feature.settings.LicensesScreen
import app.quieta.feature.settings.SettingsScreen
import app.quieta.feature.settings.ThemeScreen
import app.quieta.core.settings.ThemeMode
import app.quieta.ui.glass.FloatingBottomBar
import app.quieta.ui.glass.FloatingBottomBarDefaults
import app.quieta.ui.glass.FloatingBottomBarMode
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

private val tabRoutes = listOf(
    QuietaRoutes.HOME,
    QuietaRoutes.CONFIG,
    QuietaRoutes.RECORD,
    QuietaRoutes.SETTINGS,
)

@Composable
fun QuietaRoot() {
    var selectedRoute by rememberSaveable { mutableStateOf(QuietaRoutes.HOME) }
    var blurEnabled by rememberSaveable { mutableStateOf(true) }
    var secondaryStack by rememberSaveable { mutableStateOf(listOf<String>()) }
    val homeViewModel: HomeViewModel = viewModel()
    val context = LocalContext.current
    val pageStateHolder = rememberSaveableStateHolder()

    // LibChecker-style incremental package updates — never full rescan on install/remove.
    DisposableEffect(homeViewModel) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val pkg = intent?.data?.schemeSpecificPart ?: return
                val removed = intent.action == Intent.ACTION_PACKAGE_REMOVED
                homeViewModel.onPackageChanged(pkg, removed)
            }
        }
        context.registerReceiver(receiver, filter)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    val secondary = secondaryStack.lastOrNull()
    if (secondary != null) {
        BackHandler { secondaryStack = secondaryStack.dropLast(1) }
        when (secondary) {
            "licenses" -> LicensesScreen(onBack = { secondaryStack = secondaryStack.dropLast(1) }, blurEnabled = blurEnabled)
            "privilege" -> {
        val homeState by homeViewModel.state.collectAsStateWithLifecycle()
        PrivilegeScreen(
            selected = homeState.preferredAuthorizer,
            rootAvailable = homeState.rootAvailable,
            rootLabel = homeState.rootLabel,
            rootDescription = homeState.rootDescription,
            shizukuAvailable = homeState.shizukuAvailable,
            shizukuAuthorized = homeState.shizukuAuthorized,
            dhizukuAvailable = homeState.dhizukuAvailable,
            onBack = { secondaryStack = secondaryStack.dropLast(1) },
            onSelect = { homeViewModel.setPreferredAuthorizer(it) },
            blurEnabled = blurEnabled,
        )
            }
            "theme" -> {
                val settingsViewModel: app.quieta.feature.settings.SettingsViewModel = viewModel()
                val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
                ThemeScreen(themeMode, settingsViewModel::setThemeMode, { secondaryStack = secondaryStack.dropLast(1) }, blurEnabled)
            }
        }
        return
    }

    pageStateHolder.SaveableStateProvider("main_pages") {
        val tabs = listOf(
            QuietaNavTab(QuietaRoutes.HOME, stringResource(R.string.nav_home), Icons.Outlined.Home),
            QuietaNavTab(QuietaRoutes.CONFIG, stringResource(R.string.nav_config), Icons.AutoMirrored.Outlined.Rule),
            QuietaNavTab(QuietaRoutes.RECORD, stringResource(R.string.nav_record), Icons.Outlined.History),
            QuietaNavTab(QuietaRoutes.SETTINGS, stringResource(R.string.nav_settings), Icons.Outlined.Settings),
        )

        val liquidSupported = android.os.Build.VERSION.SDK_INT >= 33
        val mode = resolveBottomBarMode(blurEnabled, liquidSupported)
        val useShader = mode != FloatingBottomBarMode.None
        val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
        val pageBackdrop = rememberLayerBackdrop(
            onDraw = {
                drawRect(surfaceColor)
                drawContent()
            },
        )

        // InstallerX-style HorizontalPager page switch (EaseInOut custom scroll).
        val coroutineScope = rememberCoroutineScope()
        val pagerState = rememberPagerState(initialPage = tabRoutes.indexOf(selectedRoute).coerceAtLeast(0)) {
            tabRoutes.size
        }
        val mainPagerState = rememberMainPagerState(pagerState, coroutineScope)

        LaunchedEffect(selectedRoute) {
            val target = tabRoutes.indexOf(selectedRoute).coerceAtLeast(0)
            if (pagerState.currentPage != target) {
                mainPagerState.animateToPage(target)
            }
        }

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { page ->
                mainPagerState.syncPage()
                val route = tabRoutes.getOrNull(page)
                if (route != null && route != selectedRoute) {
                    selectedRoute = route
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (useShader) Modifier.layerBackdrop(pageBackdrop) else Modifier,
                    ),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = true,
                ) { page ->
                    when (tabRoutes[page]) {
                        QuietaRoutes.HOME -> HomeScreen(
                            modifier = Modifier.fillMaxSize(),
                            viewModel = homeViewModel,
                            onOpenPrivilege = { secondaryStack = secondaryStack + "privilege" },
                            onOpenConfig = { selectedRoute = QuietaRoutes.CONFIG },
                            blurEnabled = blurEnabled,
                        )
                        QuietaRoutes.CONFIG -> ConfigScreen(modifier = Modifier.fillMaxSize(), blurEnabled = blurEnabled)
                        QuietaRoutes.RECORD -> RecordScreen(modifier = Modifier.fillMaxSize(), blurEnabled = blurEnabled)
                        QuietaRoutes.SETTINGS -> SettingsScreen(
                            blurEnabled = blurEnabled,
                            onBlurEnabledChange = { blurEnabled = it },
                            bottomBarMode = mode,
                            onOpenLicenses = { secondaryStack = secondaryStack + "licenses" },
                            onOpenTheme = { secondaryStack = secondaryStack + "theme" },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            FloatingBottomBar(
                tabs = tabs,
                selectedRoute = tabRoutes[mainPagerState.selectedPage.coerceIn(0, tabRoutes.lastIndex)],
                onTabSelected = { route ->
                    selectedRoute = route
                    mainPagerState.animateToPage(tabRoutes.indexOf(route).coerceAtLeast(0))
                },
                mode = mode,
                backdrop = pageBackdrop,
                colors = FloatingBottomBarDefaults.colors(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
            )
        }
    }
}
