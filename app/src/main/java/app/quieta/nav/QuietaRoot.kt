package app.quieta.nav

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quieta.R
import app.quieta.core.settings.PredictiveBackAnimation
import app.quieta.core.settings.PredictiveBackExitDirection
import app.quieta.feature.home.HomeScreen
import app.quieta.feature.home.HomeViewModel
import app.quieta.feature.home.MutePreviewScreen
import app.quieta.feature.home.SilentChannelsScreen
import app.quieta.feature.privilege.PrivilegeScreen
import app.quieta.feature.record.RecordScreen
import app.quieta.feature.record.RecordViewModel
import app.quieta.feature.settings.AboutScreen
import app.quieta.feature.settings.LicensesScreen
import app.quieta.feature.settings.SettingsScreen
import app.quieta.feature.settings.SettingsViewModel
import app.quieta.feature.settings.ThemeScreen
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
    var quietMode by rememberSaveable { mutableStateOf(app.quieta.core.engine.QuietMode.SILENT_NO_SOUND.name) }
    var secondaryStack by rememberSaveable { mutableStateOf(listOf<String>()) }
    val homeViewModel: HomeViewModel = viewModel()
    val recordViewModel: RecordViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val context = LocalContext.current
    val pageStateHolder = rememberSaveableStateHolder()

    val persistedBlur by settingsViewModel.blurEnabled.collectAsStateWithLifecycle()
    val pbAnimation by settingsViewModel.predictiveBackAnimation.collectAsStateWithLifecycle()
    val pbExit by settingsViewModel.predictiveBackExitDirection.collectAsStateWithLifecycle()
    LaunchedEffect(persistedBlur) { blurEnabled = persistedBlur }

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
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val recordSelection by recordViewModel.selection.collectAsStateWithLifecycle()

    // InstallerX-aligned predictive back: system gesture progress drives the page transform.
    val pbProgress = remember { mutableFloatStateOf(0f) }
    var gestureCommitted by remember { mutableStateOf(false) }
    if (secondary != null) {
        PredictiveBackHandler(enabled = true) { progress ->
            try {
                progress.collect { edge ->
                    pbProgress.floatValue = edge.progress
                }
                gestureCommitted = true
                secondaryStack = secondaryStack.dropLast(1)
                pbProgress.floatValue = 0f
            } catch (e: kotlinx.coroutines.CancellationException) {
                pbProgress.floatValue = 0f
                throw e
            }
        }
    } else {
        pbProgress.floatValue = 0f
        gestureCommitted = false
    }

    if (secondary != null) {
        val gesture = pbProgress.floatValue
        val dirSign = when (pbExit) {
            PredictiveBackExitDirection.ALWAYS_LEFT -> -1f
            PredictiveBackExitDirection.ALWAYS_RIGHT -> 1f
            PredictiveBackExitDirection.FOLLOW_GESTURE -> 1f
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (gesture > 0f) {
                        when (pbAnimation) {
                            PredictiveBackAnimation.SCALE -> {
                                // InstallerX scaleNavTransition: min scale 0.85, drift 96dp-ish.
                                val s = 1f - 0.15f * gesture
                                scaleX = s
                                scaleY = s
                                translationX = dirSign * gesture * 96f
                                alpha = 1f - 0.35f * gesture
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                                    pivotFractionX = if (dirSign > 0) 0.8f else 0.2f,
                                    pivotFractionY = 0.5f,
                                )
                            }
                            PredictiveBackAnimation.CLASSIC -> {
                                val s = 1f - 0.1f * gesture
                                scaleX = s
                                scaleY = s
                                alpha = gesture
                                translationX = dirSign * gesture * 24f
                            }
                            PredictiveBackAnimation.AOSP, PredictiveBackAnimation.MIUIX -> {
                                val s = 1f - 0.05f * gesture
                                scaleX = s
                                scaleY = s
                                translationX = dirSign * gesture * 32f
                                alpha = 1f - 0.2f * gesture
                            }
                            PredictiveBackAnimation.NONE -> Unit
                        }
                    }
                },
        ) {
            val duration = if (gestureCommitted) 1 else 320
            AnimatedContent(
                targetState = secondary,
                transitionSpec = {
                    if (gestureCommitted) {
                        fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
                    } else {
                        // Programmatic push/pop (InstallerX MiuixDefault-like slide).
                        when {
                            initialState == null -> {
                                slideInHorizontally(tween(420)) { it } + fadeIn(tween(280)) togetherWith
                                    slideOutHorizontally(tween(420)) { -it / 3 } + fadeOut(tween(220))
                            }
                            targetState == null -> {
                                slideInHorizontally(tween(420)) { -it / 3 } + fadeIn(tween(280)) togetherWith
                                    slideOutHorizontally(tween(420)) { it } + fadeOut(tween(220))
                            }
                            else -> fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                        }
                    }
                },
                label = "secondary-nav",
            ) { key ->
                when (key) {
                    "licenses" -> LicensesScreen(
                        onBack = { secondaryStack = secondaryStack.dropLast(1) },
                        blurEnabled = blurEnabled,
                    )
                    "about" -> AboutScreen(
                        onBack = { secondaryStack = secondaryStack.dropLast(1) },
                        onOpenLicenses = { secondaryStack = secondaryStack + "licenses" },
                        blurEnabled = blurEnabled,
                    )
                    "privilege" -> PrivilegeScreen(
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
                    "theme" -> ThemeScreen(
                        onBack = { secondaryStack = secondaryStack.dropLast(1) },
                        blurEnabledForChrome = blurEnabled,
                        onBlurEnabledChange = { enabled ->
                            settingsViewModel.setBlurEnabled(enabled)
                        },
                    )
                    "mute_preview" -> MutePreviewScreen(
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
                    "rule_editor" -> {
                        val configViewModel: app.quieta.feature.config.ConfigViewModel = viewModel()
                        app.quieta.feature.config.ConfigRuleEditorScreen(
                            viewModel = configViewModel,
                            onBack = { secondaryStack = secondaryStack.dropLast(1) },
                            blurEnabled = blurEnabled,
                        )
                    }
                    "silent_channels" -> SilentChannelsScreen(
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
        val multiSelect = homeState.selectionMode || recordSelection.mode
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (useShader) Modifier.layerBackdrop(pageBackdrop) else Modifier),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !multiSelect,
                ) { page ->
                    when (tabRoutes[page]) {
                        QuietaRoutes.HOME -> HomeScreen(
                            modifier = Modifier.fillMaxSize(),
                            viewModel = homeViewModel,
                            onOpenPrivilege = { secondaryStack = secondaryStack + "privilege" },
                            onOpenConfig = { selectedRoute = QuietaRoutes.CONFIG },
                            onOpenMutePreview = { secondaryStack = secondaryStack + "mute_preview" },
                            onOpenQuietChannels = { m ->
                                quietMode = m.name
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
                            onBlurEnabledChange = { settingsViewModel.setBlurEnabled(it) },
                            bottomBarMode = mode,
                            onOpenLicenses = { secondaryStack = secondaryStack + "licenses" },
                            onOpenTheme = { secondaryStack = secondaryStack + "theme" },
                            onOpenAbout = { secondaryStack = secondaryStack + "about" },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            val bottomBarModifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            if (!multiSelect) {
                FloatingBottomBar(
                    tabs = tabs,
                    selectedRoute = tabRoutes[pagerState.currentPage.coerceIn(0, tabRoutes.lastIndex)],
                    onTabSelected = { route ->
                        selectedRoute = route
                        mainPagerState.animateToPage(tabRoutes.indexOf(route).coerceAtLeast(0))
                    },
                    mode = mode,
                    backdrop = pageBackdrop,
                    colors = FloatingBottomBarDefaults.colors(),
                    modifier = bottomBarModifier,
                )
            } else if (homeState.selectionMode) {
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
            } else if (recordSelection.mode) {
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
        }
    }
}
