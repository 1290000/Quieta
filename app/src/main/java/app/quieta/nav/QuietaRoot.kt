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
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.South
import androidx.compose.material.icons.outlined.VolumeOff
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quieta.R
import app.quieta.feature.config.ConfigScreen
import app.quieta.feature.home.HomeScreen
import app.quieta.feature.home.HomeViewModel
import app.quieta.feature.home.MutePreviewScreen
import app.quieta.feature.privilege.PrivilegeScreen
import app.quieta.feature.record.RecordScreen
import app.quieta.feature.record.RecordViewModel
import app.quieta.feature.settings.LicensesScreen
import app.quieta.feature.settings.AboutScreen
import app.quieta.feature.settings.SettingsScreen
import app.quieta.feature.settings.ThemeScreen
import app.quieta.core.settings.ThemeMode
import app.quieta.ui.glass.FloatingBottomBar
import app.quieta.ui.glass.FloatingBottomBarDefaults
import app.quieta.ui.glass.FloatingBottomBarMode
import app.quieta.ui.glass.FloatingSelectionAction
import app.quieta.ui.glass.FloatingSelectionBar
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
    val settingsViewModelForBlur: app.quieta.feature.settings.SettingsViewModel = viewModel()
    val persistedBlur by settingsViewModelForBlur.blurEnabled.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(persistedBlur) {
        blurEnabled = persistedBlur
    }
    var secondaryStack by rememberSaveable { mutableStateOf(listOf<String>()) }
    var quietMode by rememberSaveable { mutableStateOf(app.quieta.core.engine.QuietMode.SILENT_NO_SOUND.name) }
    val homeViewModel: HomeViewModel = viewModel()
    val recordViewModel: RecordViewModel = viewModel()
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
    val pbAnimation by settingsViewModelForBlur.predictiveBackAnimation.collectAsStateWithLifecycle()
    val pbExit by settingsViewModelForBlur.predictiveBackExitDirection.collectAsStateWithLifecycle()
    var lastSecondary by rememberSaveable { mutableStateOf<String?>(null) }
    // System predictive-back progress (Android 13+/HyperOS gesture). 0 = idle, 1 = commit.
    val pbProgress = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    if (secondary != null) {
        androidx.activity.compose.PredictiveBackHandler(enabled = true) { progress ->
            try {
                progress.collect { edge ->
                    pbProgress.floatValue = edge.progress
                }
                secondaryStack = secondaryStack.dropLast(1)
                pbProgress.floatValue = 0f
            } catch (e: kotlinx.coroutines.CancellationException) {
                pbProgress.floatValue = 0f
                throw e
            }
        }
        val forward = lastSecondary != secondary
        val gestureProgress = pbProgress.floatValue
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (gestureProgress > 0f) {
                        when (pbAnimation) {
                            app.quieta.core.settings.PredictiveBackAnimation.SCALE,
                            app.quieta.core.settings.PredictiveBackAnimation.CLASSIC,
                            -> {
                                val p = gestureProgress
                                scaleX = 1f - 0.1f * p
                                scaleY = 1f - 0.1f * p
                                alpha = 1f - 0.4f * p
                                val dir = when (pbExit) {
                                    app.quieta.core.settings.PredictiveBackExitDirection.ALWAYS_LEFT -> -1f
                                    app.quieta.core.settings.PredictiveBackExitDirection.ALWAYS_RIGHT -> 1f
                                    app.quieta.core.settings.PredictiveBackExitDirection.FOLLOW_GESTURE -> -1f
                                }
                                translationX = dir * p * 24f
                            }
                            app.quieta.core.settings.PredictiveBackAnimation.AOSP,
                            app.quieta.core.settings.PredictiveBackAnimation.MIUIX,
                            -> {
                                scaleX = 1f - 0.04f * gestureProgress
                                scaleY = 1f - 0.04f * gestureProgress
                                alpha = 1f - 0.25f * gestureProgress
                                translationX = -gestureProgress * 32f
                            }
                            app.quieta.core.settings.PredictiveBackAnimation.NONE -> Unit
                        }
                    }
                },
        ) {
        androidx.compose.animation.AnimatedContent(
            targetState = secondary,
            transitionSpec = {
                secondaryNavTransform(pbAnimation, pbExit, forward = forward)
            },
            label = "secondary-nav",
        ) { secondaryKey ->
            when (secondaryKey) {
            "licenses" -> LicensesScreen(onBack = { secondaryStack = secondaryStack.dropLast(1) }, blurEnabled = blurEnabled)
            "about" -> AboutScreen(
                onBack = { secondaryStack = secondaryStack.dropLast(1) },
                onOpenLicenses = { secondaryStack = secondaryStack + "licenses" },
                blurEnabled = blurEnabled,
            )
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
                val persistedBlur by settingsViewModel.blurEnabled.collectAsStateWithLifecycle()
                ThemeScreen(
                    onBack = { secondaryStack = secondaryStack.dropLast(1) },
                    blurEnabledForChrome = blurEnabled,
                    onBlurEnabledChange = { enabled ->
                        blurEnabled = enabled
                        settingsViewModel.setBlurEnabled(enabled)
                    },
                )
                // Keep local chrome in sync if another surface wrote the setting.
                androidx.compose.runtime.LaunchedEffect(persistedBlur) {
                    if (persistedBlur != blurEnabled) blurEnabled = persistedBlur
                }
            }
            "mute_preview" -> {
                val homeState by homeViewModel.state.collectAsStateWithLifecycle()
                MutePreviewScreen(
                    preview = homeState.mutePreview,
                    onBack = {
                        homeViewModel.dismissMutePreview()
                        secondaryStack = secondaryStack.dropLast(1)
                    },
                    onConfirm = {
                        homeViewModel.confirmBatchMute()
                        secondaryStack = secondaryStack.dropLast(1)
                    },
                    onScopeChange = homeViewModel::setMuteScope,
                    blurEnabled = blurEnabled,
                )
            }
            "rule_editor" -> {
                val configViewModel: app.quieta.feature.config.ConfigViewModel = viewModel()
                app.quieta.feature.config.ConfigRuleEditorScreen(
                    viewModel = configViewModel,
                    onBack = { secondaryStack = secondaryStack.dropLast(1) },
                    blurEnabled = blurEnabled,
                )
            }
            "silent_channels" -> {
                val homeState by homeViewModel.state.collectAsStateWithLifecycle()
                app.quieta.feature.home.SilentChannelsScreen(
                    apps = homeState.apps,
                    plan = homeState.plan,
                    onBack = { secondaryStack = secondaryStack.dropLast(1) },
                    onChannelAction = homeViewModel::applyChannelAction,
                    blurEnabled = blurEnabled,
                    mode = runCatching {
                        app.quieta.core.engine.QuietMode.valueOf(quietMode)
                    }.getOrDefault(app.quieta.core.engine.QuietMode.SILENT_NO_SOUND),
                )
            }
            }
        }
        }
        androidx.compose.runtime.LaunchedEffect(secondary) {
            lastSecondary = secondary
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
                            onOpenMutePreview = { secondaryStack = secondaryStack + "mute_preview" },
                            onOpenQuietChannels = { mode ->
                                quietMode = mode.name
                                secondaryStack = secondaryStack + "silent_channels"
                            },
                            blurEnabled = blurEnabled,
                        )
                        QuietaRoutes.CONFIG -> {
                            val configViewModel: app.quieta.feature.config.ConfigViewModel = viewModel()
                            app.quieta.feature.config.ConfigScreen(
                                modifier = Modifier.fillMaxSize(),
                                blurEnabled = blurEnabled,
                                viewModel = configViewModel,
                                onOpenEditor = { secondaryStack = secondaryStack + "rule_editor" },
                            )
                        }
                        QuietaRoutes.RECORD -> RecordScreen(
                            modifier = Modifier.fillMaxSize(),
                            blurEnabled = blurEnabled,
                            viewModel = recordViewModel,
                        )
                        QuietaRoutes.SETTINGS -> SettingsScreen(
                            blurEnabled = blurEnabled,
                            onBlurEnabledChange = { blurEnabled = it },
                            bottomBarMode = mode,
                            onOpenLicenses = { secondaryStack = secondaryStack + "licenses" },
                            onOpenTheme = { secondaryStack = secondaryStack + "theme" },
                            onOpenAbout = { secondaryStack = secondaryStack + "about" },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            // Multi-select action bar owns the bottom edge — hide the tab bar so it is not covered.
            val homeState by homeViewModel.state.collectAsStateWithLifecycle()
            val recordSelection by recordViewModel.selection.collectAsStateWithLifecycle()
            val bottomBarModifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))

            when {
                homeState.selectionMode -> {
                    val selectionEnabled = !homeState.checkingPrivilege &&
                        homeState.selectedChannelKeys.isNotEmpty() &&
                        homeState.progress == null
                    FloatingSelectionBar(
                        countLabel = "已选 ${homeState.selectedChannelKeys.size}",
                        busy = homeState.progress != null,
                        mode = mode,
                        backdrop = pageBackdrop,
                        modifier = bottomBarModifier,
                        actions = listOf(
                            FloatingSelectionAction(
                                id = "mute",
                                label = "静音",
                                icon = Icons.Outlined.VolumeOff,
                                enabled = selectionEnabled,
                                emphasized = true,
                                onClick = {
                                    homeViewModel.applySelectionAction(app.quieta.core.model.RuleAction.MUTE)
                                },
                            ),
                            FloatingSelectionAction(
                                id = "downgrade",
                                label = "降级",
                                icon = Icons.Outlined.South,
                                enabled = selectionEnabled,
                                onClick = {
                                    homeViewModel.applySelectionAction(app.quieta.core.model.RuleAction.DOWNGRADE)
                                },
                            ),
                            FloatingSelectionAction(
                                id = "keep",
                                label = "保留",
                                icon = Icons.Outlined.Restore,
                                enabled = selectionEnabled,
                                onClick = {
                                    homeViewModel.applySelectionAction(app.quieta.core.model.RuleAction.KEEP)
                                },
                            ),
                        ),
                    )
                }
                recordSelection.mode -> {
                    val selectionEnabled = recordSelection.selectedKeys.isNotEmpty() && !recordSelection.busy
                    FloatingSelectionBar(
                        countLabel = "已选 ${recordSelection.selectedKeys.size}",
                        busy = recordSelection.busy,
                        mode = mode,
                        backdrop = pageBackdrop,
                        modifier = bottomBarModifier,
                        actions = listOf(
                            FloatingSelectionAction(
                                id = "mute",
                                label = "静音",
                                icon = Icons.Outlined.VolumeOff,
                                enabled = selectionEnabled,
                                emphasized = true,
                                onClick = {
                                    recordViewModel.applySelectionAction(app.quieta.core.model.RuleAction.MUTE)
                                },
                            ),
                            FloatingSelectionAction(
                                id = "downgrade",
                                label = "降级",
                                icon = Icons.Outlined.South,
                                enabled = selectionEnabled,
                                onClick = {
                                    recordViewModel.applySelectionAction(app.quieta.core.model.RuleAction.DOWNGRADE)
                                },
                            ),
                            FloatingSelectionAction(
                                id = "keep",
                                label = "保留",
                                icon = Icons.Outlined.Restore,
                                enabled = selectionEnabled,
                                onClick = {
                                    recordViewModel.applySelectionAction(app.quieta.core.model.RuleAction.KEEP)
                                },
                            ),
                        ),
                    )
                }
                else -> {
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
                        modifier = bottomBarModifier,
                    )
                }
            }
        }
    }
}
