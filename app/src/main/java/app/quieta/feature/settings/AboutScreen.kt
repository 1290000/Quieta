// SPDX-License-Identifier: GPL-3.0-only
// About page ported toward InstallerX Revived MiuixAboutPage (AGSL bg + scroll-fade hero).
package app.quieta.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quieta.BuildConfig
import app.quieta.R
import app.quieta.ui.component.QuietaPage
import app.quieta.ui.effect.bg.BgEffectBackground
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.preference.ArrowPreference

// InstallerX Revived hero palette (mauve / lavender).
private val AboutTitleLight = Color(0xFF7A4A6E)
private val AboutTitleDark = Color(0xFFE8C4DC)
private val AboutVersionLight = Color(0xFF6B5A72)
private val AboutVersionDark = Color(0xADA8A8B0)

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
    val lazyListState = rememberLazyListState()
    val shaderOk = remember { isRuntimeShaderSupported() }

    val scrollProgress by remember(lazyListState) {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex > 0) 1f
            else {
                val offset = lazyListState.firstVisibleItemScrollOffset
                (offset / 420f).coerceIn(0f, 1f)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        BgEffectBackground(
            dynamicBackground = shaderOk,
            isOs3Effect = true,
            modifier = Modifier.fillMaxSize(),
            bgModifier = Modifier.fillMaxSize(),
            alpha = { 1f - scrollProgress },
        ) {
            QuietaPage(
                title = stringResource(R.string.about),
                blurEnabled = blurEnabled,
                state = lazyListState,
                itemSpacing = 0.dp,
                bottomPadding = 28.dp,
                topPadding = 0.dp,
                containerColor = Color.Transparent,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            ) {
                item(key = "hero") {
                    AboutHero(
                        isDark = isDark,
                        topPadding = 48.dp,
                        versionText = versionInfoText(),
                        scrollProgress = scrollProgress,
                    )
                }
                item(key = "about-content") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = safeInsets.calculateStartPadding(layoutDirection),
                                end = safeInsets.calculateEndPadding(layoutDirection),
                                bottom = 0.dp,
                            ),
                    ) {
                        SmallTitle(
                            text = stringResource(R.string.about),
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                        )
                        AboutActionCard(
                            sourceSubtitle = stringResource(R.string.about_source_desc),
                            licenseSubtitle = stringResource(R.string.about_licenses_desc),
                            updateSubtitle = updateState.message
                                ?: stringResource(R.string.about_check_update_desc),
                            updateChecking = updateState.checking,
                            releaseUrl = updateState.releaseUrl,
                            onSourceClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl)))
                                }
                            },
                            onLicenseClick = onOpenLicenses,
                            onUpdateClick = { viewModel.checkUpdate() },
                            onOpenRelease = { url ->
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

@Composable
private fun versionInfoText(): String {
    val level = if (BuildConfig.DEBUG) "调试版" else "正式版"
    return "$level ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
}

/**
 * InstallerX hero: icon/title/version fade and slightly shrink as the list scrolls.
 */
@Composable
private fun AboutHero(
    isDark: Boolean,
    topPadding: Dp,
    versionText: String,
    scrollProgress: Float,
) {
    val context = LocalContext.current
    val iconBitmap = remember(context) {
        runCatching {
            context.packageManager.getApplicationIcon(context.packageName)
                .toBitmap(192, 192)
                .asImageBitmap()
        }.getOrNull()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer {
                    val iconProgress = ((scrollProgress - 0.35f) / 0.15f).coerceIn(0f, 1f)
                    alpha = 1f - iconProgress
                    scaleX = 1f - (iconProgress * 0.05f)
                    scaleY = 1f - (iconProgress * 0.05f)
                },
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(88.dp),
                )
            }
        }
        Text(
            modifier = Modifier
                .padding(top = 12.dp, bottom = 5.dp)
                .graphicsLayer {
                    val nameProgress = ((scrollProgress - 0.20f) / 0.15f).coerceIn(0f, 1f)
                    alpha = 1f - nameProgress
                    scaleX = 1f - (nameProgress * 0.05f)
                    scaleY = 1f - (nameProgress * 0.05f)
                },
            text = stringResource(R.string.app_name),
            fontWeight = FontWeight.Bold,
            fontSize = 35.sp,
            color = if (isDark) AboutTitleDark else AboutTitleLight,
            textAlign = TextAlign.Center,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val verProgress = ((scrollProgress - 0.05f) / 0.15f).coerceIn(0f, 1f)
                    alpha = 1f - verProgress
                    scaleX = 1f - (verProgress * 0.05f)
                    scaleY = 1f - (verProgress * 0.05f)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = versionText,
                fontSize = 14.sp,
                color = if (isDark) AboutVersionDark else AboutVersionLight,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AboutActionCard(
    sourceSubtitle: String,
    licenseSubtitle: String,
    updateSubtitle: String,
    updateChecking: Boolean,
    releaseUrl: String?,
    onSourceClick: () -> Unit,
    onLicenseClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onOpenRelease: (String) -> Unit,
) {
    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        cornerRadius = 16.dp,
        colors = MiuixCardDefaults.defaultColors(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        // InstallerX MiuixNavigationItemWidget → ArrowPreference
        ArrowPreference(
            title = stringResource(R.string.about_source),
            summary = sourceSubtitle,
            onClick = onSourceClick,
        )
        ArrowPreference(
            title = stringResource(R.string.about_licenses),
            summary = licenseSubtitle,
            onClick = onLicenseClick,
        )
        ArrowPreference(
            title = stringResource(R.string.about_check_update),
            summary = updateSubtitle,
            onClick = onUpdateClick,
        )
        releaseUrl?.let { url ->
            ArrowPreference(
                title = "打开 Release 页",
                summary = url,
                onClick = { onOpenRelease(url) },
            )
        }
        if (updateChecking) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InfiniteProgressIndicator(modifier = Modifier.size(16.dp))
                Text("检查中…", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
