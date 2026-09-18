package app.quieta.nav

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.South
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quieta.R
import app.quieta.core.settings.PredictiveBackAnimation
import app.quieta.feature.config.ConfigRuleEditorScreen
import app.quieta.feature.config.ConfigScreen
import app.quieta.feature.config.ConfigUiEvent
import app.quieta.feature.config.ConfigViewModel
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val configViewModel: ConfigViewModel = viewModel()
    val context = LocalContext.current
    val pageStateHolder = rememberSaveableStateHolder()
    val coroutineScope = rememberCoroutineScope()

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
    val configState by configViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(configViewModel) {
        configViewModel.events.collect { event ->
            when (event) {
                is ConfigUiEvent.ShareRules -> {
                    runCatching { context.startActivity(event.intent) }
                }
                is ConfigUiEvent.ShowError -> {
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(homeViewModel) {
        homeViewModel.events.collect { event ->
            when (event) {
                is app.quieta.feature.home.HomeUiEvent.ShareSnapshot -> {
                    runCatching { context.startActivity(event.intent) }
                }
                is app.quieta.feature.home.HomeUiEvent.ShowMessage -> {
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(settingsViewModel) {
        settingsViewModel.events.collect { event ->
            when (event) {
                is app.quieta.feature.settings.SettingsUiEvent.ShareSnapshot -> {
                    runCatching { context.startActivity(event.intent) }
                }
                is app.quieta.feature.settings.SettingsUiEvent.ShowMessage -> {
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val driver = remember { PredictiveBackDriver() }
    var layoutSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val rtl = layoutDirection == LayoutDirection.Rtl

    LaunchedEffect(pbAnimation, pbExit) {
        driver.animation = pbAnimation
        driver.exitDirection = pbExit
    }

    /**
     * InstallerX-aligned push: arm the enter pose BEFORE the route is composed so the first
     * frame is already off-screen/translated, then play MiuixDefault (or AOSP open) motion.
     */
    val openSecondary: (String) -> Unit = route@{
        if (driver.phase != PredictiveNavPhase.Idle) return@route
        if (secondaryStack.lastOrNull() == it) return@route
        driver.animation = pbAnimation
        driver.exitDirection = pbExit
        driver.beginPush()
        secondaryStack = secondaryStack + it
        coroutineScope.launch {
            animatePredictiveSettle(driver, driver.animation, PredictiveNavPhase.Push)
            driver.reset()
        }
    }

    val popSecondary: () -> Unit = {
        if (secondaryStack.isNotEmpty() &&
            driver.phase != PredictiveNavPhase.Commit &&
            driver.phase != PredictiveNavPhase.Cancel &&
            driver.phase != PredictiveNavPhase.Pop &&
            driver.phase != PredictiveNavPhase.Push
        ) {
            coroutineScope.launch {
                if (secondaryStack.isEmpty() || driver.phase != PredictiveNavPhase.Idle) return@launch
                driver.animation = pbAnimation
                driver.exitDirection = pbExit
                driver.beginPop()
                animatePredictiveSettle(driver, driver.animation, PredictiveNavPhase.Pop)
                if (secondaryStack.isNotEmpty()) {
                    secondaryStack = secondaryStack.dropLast(1)
                }
                driver.reset()
            }
        }
    }

    PredictiveBackHandler(enabled = secondary != null) { progress ->
        driver.animation = pbAnimation
        driver.exitDirection = pbExit
        try {
            progress.collect { event: BackEventCompat ->
                driver.onGestureEvent(event)
            }
            driver.beginCommit()
            animatePredictiveSettle(driver, driver.animation, PredictiveNavPhase.Commit)
            secondaryStack = secondaryStack.dropLast(1)
            driver.reset()
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                driver.beginCancel()
                animatePredictiveSettle(driver, driver.animation, PredictiveNavPhase.Cancel)
                driver.reset()
            }
            throw e
        }
    }

    // Subscribe only to discrete phase changes in composition; continuous progress is
    // read inside graphicsLayer so gesture frames invalidate draw, not full recomposition.
    val phase = driver.phase
    val secondaryOpen = secondary != null
    val showUnderlay = !secondaryOpen || phase != PredictiveNavPhase.Idle
    // Previous destination: nested secondary under top, else main pager.
    val underlayRoute = secondaryStack.getOrNull(secondaryStack.lastIndex - 1)
    val widthPx = layoutSize.width.toFloat()
    val heightPx = layoutSize.height.toFloat()
    val roundAll = predictiveRoundAllCorners(pbAnimation)
    val cardStyle = secondaryOpen && predictiveCardStyle(pbAnimation) && phase != PredictiveNavPhase.Idle

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { layoutSize = it },
    ) {
        if (showUnderlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Observe driver in the draw phase so gesture frames invalidate without
                    // recomposing the main pager on every progress tick.
                    .drawBehind {
                        driver.leaveProgress
                        driver.phase
                        driver.settleEased
                        driver.swipeEdge
                    }
                    .graphicsLayer {
                        if (!secondaryOpen) {
                            scaleX = 1f
                            scaleY = 1f
                            translationX = 0f
                            translationY = 0f
                            alpha = 1f
                            transformOrigin = TransformOrigin.Center
                        } else {
                            val underlayTransform = predictiveUnderlayTransform(
                                animation = driver.animation,
                                phase = driver.phase,
                                leaveProgress = driver.leaveProgress,
                                gestureProgress = driver.gestureProgress,
                                releaseProgress = driver.releaseProgress,
                                settleEased = driver.settleEased,
                                swipeEdge = driver.swipeEdge,
                                touchY = driver.touchY,
                                initialTouchY = driver.initialTouchY,
                                widthPx = widthPx,
                                heightPx = heightPx,
                                density = density,
                                rtl = rtl,
                            )
                            scaleX = underlayTransform.scaleX
                            scaleY = underlayTransform.scaleY
                            translationX = underlayTransform.translationX
                            translationY = underlayTransform.translationY
                            alpha = underlayTransform.alpha
                            transformOrigin = underlayTransform.transformOrigin
                        }
                    },
            ) {
                if (underlayRoute == null) {
                    MainPagerLayer(
                        pageStateHolder = pageStateHolder,
                        selectedRoute = selectedRoute,
                        onSelectedRouteChange = { selectedRoute = it },
                        homeViewModel = homeViewModel,
                        recordViewModel = recordViewModel,
                        settingsViewModel = settingsViewModel,
                        configViewModel = configViewModel,
                        homeState = homeState,
                        recordSelection = recordSelection,
                        configState = configState,
                        blurEnabled = blurEnabled,
                        quietMode = quietMode,
                        onQuietModeChange = { quietMode = it },
                        onOpenSecondary = openSecondary,
                    )
                } else {
                    // Nested secondary is the covered layer during push/pop/predictive back.
                    SecondaryPageLayer(
                        route = underlayRoute,
                        onBack = { },
                        onOpenSecondary = { },
                        homeViewModel = homeViewModel,
                        settingsViewModel = settingsViewModel,
                        homeState = homeState,
                        blurEnabled = blurEnabled,
                        quietMode = quietMode,
                    )
                }
            }
        }

        if (secondaryOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        driver.leaveProgress
                        driver.phase
                        driver.settleRaw
                    }
                    .graphicsLayer {
                        // Dim scrim as a layer alpha on a full-bleed black plate.
                        alpha = predictiveScrimAlpha(
                            animation = driver.animation,
                            phase = driver.phase,
                            leaveProgress = driver.leaveProgress,
                            settleRaw = driver.settleRaw,
                        )
                    }
                    .background(Color.Black),
            )

            val pageBg = MaterialTheme.colorScheme.surfaceContainer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        driver.leaveProgress
                        driver.phase
                        driver.settleEased
                        driver.settleRaw
                        driver.swipeEdge
                        driver.touchY
                    }
                    .graphicsLayer {
                        val outgoing = predictiveOutgoingTransform(
                            animation = driver.animation,
                            exitDirection = driver.exitDirection,
                            phase = driver.phase,
                            leaveProgress = driver.leaveProgress,
                            gestureProgress = driver.gestureProgress,
                            releaseProgress = driver.releaseProgress,
                            settleEased = driver.settleEased,
                            settleRaw = driver.settleRaw,
                            swipeEdge = driver.swipeEdge,
                            touchY = driver.touchY,
                            initialTouchY = driver.initialTouchY,
                            widthPx = widthPx,
                            heightPx = heightPx,
                            density = density,
                            rtl = rtl,
                        )
                        scaleX = outgoing.scaleX
                        scaleY = outgoing.scaleY
                        translationX = outgoing.translationX
                        translationY = outgoing.translationY
                        alpha = outgoing.alpha
                        transformOrigin = outgoing.transformOrigin
                    }
                    .then(
                        if (cardStyle && roundAll) {
                            Modifier.clip(RoundedCornerShape(32.dp))
                        } else {
                            Modifier
                        },
                    )
                    .background(pageBg),
            ) {
                SecondaryPageLayer(
                    route = secondary,
                    onBack = popSecondary,
                    onOpenSecondary = openSecondary,
                    homeViewModel = homeViewModel,
                    settingsViewModel = settingsViewModel,
                    homeState = homeState,
                    blurEnabled = blurEnabled,
                    quietMode = quietMode,
                )
            }
        }
    }
}

@Composable
private fun MainPagerLayer(
    pageStateHolder: androidx.compose.runtime.saveable.SaveableStateHolder,
    selectedRoute: String,
    onSelectedRouteChange: (String) -> Unit,
    homeViewModel: HomeViewModel,
    recordViewModel: RecordViewModel,
    settingsViewModel: SettingsViewModel,
    configViewModel: ConfigViewModel,
    homeState: app.quieta.feature.home.HomeUiState,
    recordSelection: app.quieta.feature.record.RecordSelectionUiState,
    configState: app.quieta.feature.config.ConfigUiState,
    blurEnabled: Boolean,
    quietMode: String,
    onQuietModeChange: (String) -> Unit,
    onOpenSecondary: (String) -> Unit,
) {
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
                // InstallerX: sync MainPagerState from pager page, but never while
                // animateToPage owns the selection — mid-scroll updates desync the bar.
                if (!mainPagerState.isNavigating) {
                    mainPagerState.syncPage()
                    val route = tabRoutes.getOrNull(page)
                    if (route != null && route != selectedRoute) {
                        onSelectedRouteChange(route)
                    }
                }
            }
        }
        val multiSelect = homeState.selectionMode || recordSelection.mode || configState.selectionMode
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
                            onOpenPrivilege = { onOpenSecondary("privilege") },
                            onOpenConfig = { onSelectedRouteChange(QuietaRoutes.CONFIG) },
                            onOpenMutePreview = { onOpenSecondary("mute_preview") },
                            onOpenQuietChannels = { m ->
                                onQuietModeChange(m.name)
                                onOpenSecondary("silent_channels")
                            },
                            blurEnabled = blurEnabled,
                        )
                        QuietaRoutes.CONFIG -> {
                            ConfigScreen(
                                modifier = Modifier.fillMaxSize(),
                                blurEnabled = blurEnabled,
                                viewModel = configViewModel,
                                onOpenEditor = { onOpenSecondary("rule_editor") },
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
                            onOpenLicenses = { onOpenSecondary("licenses") },
                            onOpenTheme = { onOpenSecondary("theme") },
                            onOpenAbout = { onOpenSecondary("about") },
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
                    // InstallerX: bind the bar to MainPagerState.selectedPage (target), not
                    // pager.currentPage (mid-scroll), so the pill never lags or double-jumps.
                    selectedRoute = tabRoutes[
                        mainPagerState.selectedPage.coerceIn(0, tabRoutes.lastIndex),
                    ],
                    onTabSelected = { route ->
                        onSelectedRouteChange(route)
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
                        FloatingSelectionAction(
                            id = "export",
                            label = "导出",
                            icon = Icons.Outlined.Share,
                            enabled = homeState.selectedChannelKeys.isNotEmpty() && homeState.progress == null,
                            onClick = { homeViewModel.exportSelectionSnapshot() },
                        ),
                    ),
                )
            } else if (configState.selectionMode) {
                val selectionEnabled = configState.selectedRuleIds.isNotEmpty()
                FloatingSelectionBar(
                    countLabel = "已选 ${configState.selectedRuleIds.size}",
                    busy = false,
                    mode = mode,
                    backdrop = pageBackdrop,
                    modifier = bottomBarModifier,
                    actions = listOf(
                        FloatingSelectionAction(
                            id = "export",
                            label = "导出",
                            icon = Icons.Outlined.Share,
                            enabled = selectionEnabled,
                            emphasized = true,
                            onClick = { configViewModel.exportAndShare(onlySelected = true) },
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

@Composable
private fun SecondaryPageLayer(
    route: String,
    onBack: () -> Unit,
    onOpenSecondary: (String) -> Unit,
    homeViewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    homeState: app.quieta.feature.home.HomeUiState,
    blurEnabled: Boolean,
    quietMode: String,
) {
    when (route) {
        "licenses" -> LicensesScreen(
            onBack = onBack,
            blurEnabled = blurEnabled,
        )
        "about" -> AboutScreen(
            onBack = onBack,
            onOpenLicenses = { onOpenSecondary("licenses") },
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
            onBack = onBack,
            onSelect = { homeViewModel.setPreferredAuthorizer(it) },
            blurEnabled = blurEnabled,
        )
        "theme" -> ThemeScreen(
            onBack = onBack,
            blurEnabledForChrome = blurEnabled,
            onBlurEnabledChange = { enabled ->
                settingsViewModel.setBlurEnabled(enabled)
            },
        )
        "mute_preview" -> MutePreviewScreen(
            preview = homeState.mutePreview,
            onBack = {
                homeViewModel.dismissMutePreview()
                onBack()
            },
            onConfirm = {
                homeViewModel.confirmBatchMute()
                onBack()
            },
            onScopeChange = homeViewModel::setMuteScope,
            blurEnabled = blurEnabled,
        )
        "rule_editor" -> {
            val configViewModel: ConfigViewModel = viewModel()
            ConfigRuleEditorScreen(
                viewModel = configViewModel,
                onBack = onBack,
                blurEnabled = blurEnabled,
            )
        }
        "silent_channels" -> SilentChannelsScreen(
            apps = homeState.apps,
            plan = homeState.plan,
            onBack = onBack,
            onChannelAction = homeViewModel::applyChannelAction,
            blurEnabled = blurEnabled,
            mode = runCatching {
                app.quieta.core.engine.QuietMode.valueOf(quietMode)
            }.getOrDefault(app.quieta.core.engine.QuietMode.SILENT_NO_SOUND),
        )
    }
}
