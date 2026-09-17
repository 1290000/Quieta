// SPDX-License-Identifier: GPL-3.0-only
// About page ported from InstallerX Revived MiuixAboutPage (AGSL + textureBlur + scroll-fade chrome).
package app.quieta.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quieta.BuildConfig
import app.quieta.R
import app.quieta.ui.effect.bg.BgEffectBackground
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

// InstallerX hero palette.
private val AboutTitleLight = Color(0xFF7A4A6E)
private val AboutTitleDark = Color(0xFFE8C4DC)
private val AboutVersionLight = Color(0xFF6B5A72)
private val AboutVersionDark = Color(0xADA8A8B0)

// InstallerX MiuixAboutPage logoBlend (aurora fill through glyph alpha).
private fun aboutLogoBlend(isDark: Boolean): List<BlendColorEntry> = if (isDark) {
    listOf(
        BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
        BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
        BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
    )
} else {
    listOf(
        BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
        BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
        BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
    )
}

// InstallerX ColorBlendToken.Pured_Regular_Light / Overlay_Extra_Thin_Dark
private fun aboutCardBlend(isDark: Boolean): List<BlendColorEntry> = if (isDark) {
    listOf(
        BlendColorEntry(Color(0x75000000), BlurBlendMode.ColorBurn),
        BlendColorEntry(Color(0x52000000), BlurBlendMode.SrcOver),
    )
} else {
    listOf(
        BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
        BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
    )
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
    blurEnabled: Boolean = true,
    viewModel: AboutViewModel = viewModel(),
) {
    val context = LocalContext.current
    val repoUrl = stringResource(R.string.repo_url)
    val updateState by viewModel.state.collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()
    val layoutDirection = LocalLayoutDirection.current
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val shaderOk = remember { isRuntimeShaderSupported() }
    val blurOk = blurEnabled && shaderOk

    val lazyListState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    var heroHeightPx by remember { mutableIntStateOf(0) }

    val scrollProgress by remember(lazyListState, heroHeightPx) {
        derivedStateOf {
            if (heroHeightPx <= 0) {
                (lazyListState.firstVisibleItemScrollOffset / 420f).coerceIn(0f, 1f)
            } else {
                val index = lazyListState.firstVisibleItemIndex
                val offset = lazyListState.firstVisibleItemScrollOffset
                if (index > 0) 1f else (offset.toFloat() / heroHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    val cardBackdrop = if (blurOk) {
        rememberLayerBackdrop {
            // Capture whatever is behind cards (aurora / BgEffect).
            drawContent()
        }
    } else {
        null
    }
    // Tap hero mark to flip InstallerX aurora fill ↔ original brand colors.
    var auroraTintLogo by rememberSaveable { mutableStateOf(true) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.about),
                // InstallerX: title only appears after scrolling past hero.
                color = if (blurOk && scrollProgress >= 1f) {
                    Color.Transparent
                } else if (scrollProgress >= 1f) {
                    MiuixTheme.colorScheme.surface
                } else {
                    Color.Transparent
                },
                titleColor = if (isDark) AboutTitleDark else AboutTitleLight,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                            tint = if (isDark) AboutTitleDark else AboutTitleLight,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val listContentPadding = PaddingValues(
            start = safeInsets.calculateStartPadding(layoutDirection),
            top = innerPadding.calculateTopPadding(),
            end = safeInsets.calculateEndPadding(layoutDirection),
            bottom = 28.dp,
        )
        val logoTop = innerPadding.calculateTopPadding() + 40.dp
        val density = LocalDensity.current

        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            BgEffectBackground(
                dynamicBackground = shaderOk,
                isOs3Effect = true,
                modifier = Modifier.fillMaxSize(),
                // Only the aurora spacer is recorded — wrapping cards in the same
                // LayerBackdrop recurses HWUI on HyperOS (prepareTreeImpl SIGSEGV).
                bgModifier = Modifier
                    .fillMaxSize()
                    .then(cardBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier),
                alpha = { 1f - scrollProgress },
            ) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = listContentPadding,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(0.dp),
                ) {
                    item(key = "hero") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 52.dp, bottom = 56.dp)
                                .onSizeChanged { size -> heroHeightPx = size.height },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AboutHeroIcon(
                                scrollProgress = scrollProgress,
                                backdrop = cardBackdrop,
                                isDark = isDark,
                                blurOk = blurOk,
                                auroraTint = auroraTintLogo,
                                onToggle = { auroraTintLogo = !auroraTintLogo },
                            )
                            Text(
                                modifier = Modifier
                                    .padding(top = 12.dp, bottom = 5.dp)
                                    .graphicsLayer {
                                        val p = ((scrollProgress - 0.20f) / 0.15f).coerceIn(0f, 1f)
                                        alpha = 1f - p
                                        scaleX = 1f - (p * 0.05f)
                                        scaleY = 1f - (p * 0.05f)
                                    },
                                text = stringResource(R.string.app_name) + " " + stringResource(R.string.app_name_en),
                                fontWeight = FontWeight.Bold,
                                fontSize = 35.sp,
                                color = if (isDark) AboutTitleDark else AboutTitleLight,
                                textAlign = TextAlign.Center,
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        val p = ((scrollProgress - 0.05f) / 0.15f).coerceIn(0f, 1f)
                                        alpha = 1f - p
                                        scaleX = 1f - (p * 0.05f)
                                        scaleY = 1f - (p * 0.05f)
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = versionInfoText(),
                                    fontSize = 14.sp,
                                    color = if (isDark) AboutVersionDark else AboutVersionLight,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    item(key = "about-content") {
                        SmallTitle(stringResource(R.string.about))
                        AboutGlassCard(
                            backdrop = cardBackdrop,
                            isDark = isDark,
                            blurOk = blurOk,
                        ) {
                            ArrowPreference(
                                title = stringResource(R.string.about_source),
                                summary = stringResource(R.string.about_source_desc),
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl)))
                                    }
                                },
                            )
                            ArrowPreference(
                                title = stringResource(R.string.about_licenses),
                                summary = stringResource(R.string.about_licenses_desc),
                                onClick = onOpenLicenses,
                            )
                            ArrowPreference(
                                title = stringResource(R.string.about_check_update),
                                summary = updateState.message
                                    ?: stringResource(R.string.about_check_update_desc),
                                onClick = { viewModel.checkUpdate() },
                            )
                            if (updateState.checking) {
                                BasicComponent(
                                    title = "检查中",
                                    summary = "正在检查软件更新",
                                    endActions = {
                                        InfiniteProgressIndicator(modifier = Modifier.size(18.dp))
                                    },
                                )
                            }
                            updateState.releaseUrl?.let { url ->
                                ArrowPreference(
                                    title = "打开 Release 页",
                                    summary = url,
                                    onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun versionInfoText(): String {
    val level = if (BuildConfig.DEBUG) "调试版" else "正式版"
    return "$level ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
}

@Composable
private fun AboutHeroIcon(
    scrollProgress: Float,
    backdrop: LayerBackdrop?,
    isDark: Boolean,
    blurOk: Boolean,
    auroraTint: Boolean,
    onToggle: () -> Unit,
) {
    val logoBlend = remember(isDark) { aboutLogoBlend(isDark) }
    val useAurora = auroraTint && backdrop != null && blurOk
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(8.dp)
            .size(88.dp)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClickLabel = "切换图标样式",
                onClick = onToggle,
            )
            .graphicsLayer {
                val iconProgress = ((scrollProgress - 0.35f) / 0.15f).coerceIn(0f, 1f)
                alpha = 1f - iconProgress
                scaleX = 1f - (iconProgress * 0.05f)
                scaleY = 1f - (iconProgress * 0.05f)
            },
    ) {
        // Default: InstallerX aurora fill through glyph alpha (DstIn).
        // Tap again: original green/teal brand colors.
        if (useAurora) {
            Image(
                painter = painterResource(R.drawable.ic_about_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(88.dp)
                    .textureBlur(
                        backdrop = backdrop!!,
                        shape = RoundedCornerShape(12.dp),
                        blurRadius = 200f,
                        noiseCoefficient = 0.02f,
                        colors = BlurColors(blendColors = logoBlend),
                        contentBlendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                        enabled = true,
                    ),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_about_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(88.dp),
            )
        }
    }
}

/**
 * InstallerX card: textureBlur over the aurora, transparent container when blur is on.
 */
@Composable
private fun AboutGlassCard(
    backdrop: LayerBackdrop?,
    isDark: Boolean,
    blurOk: Boolean,
    content: @Composable () -> Unit,
) {
    val shape = remember { RoundedCornerShape(16.dp) }
    val blend = remember(isDark) { aboutCardBlend(isDark) }
    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .then(
                if (backdrop != null && blurOk) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = shape,
                        blurRadius = 60f,
                        noiseCoefficient = 0.02f,
                        colors = BlurColors(blendColors = blend),
                        enabled = true,
                    )
                } else {
                    Modifier
                },
            ),
        cornerRadius = 16.dp,
        colors = MiuixCardDefaults.defaultColors(
            color = if (backdrop != null && blurOk) {
                Color.Transparent
            } else {
                MiuixTheme.colorScheme.surfaceContainer
            },
            contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
        ),
    ) {
        content()
    }
}
