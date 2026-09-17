// SPDX-License-Identifier: GPL-3.0-only
// About page aligned with InstallerX Revived MiuixAboutPage (aurora hero + grouped card).
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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quieta.BuildConfig
import app.quieta.R
import app.quieta.ui.component.QuietaPage
import app.quieta.ui.effect.AboutGradientBackground
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle

// InstallerX Revived hero palette (mauve / lavender aurora).
private val AboutTitleLight = Color(0xFF7A4A6E)
private val AboutTitleDark = Color(0xFFE8C4DC)
private val AboutVersionLight = Color(0xFF6B5A72)
private val AboutVersionDark = Color(0xADA8A8B0)

/**
 * InstallerX-aligned about: full-bleed aurora, centered mark, 35sp title, grouped card.
 */
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

    Box(modifier = modifier.fillMaxSize()) {
        AboutGradientBackground(isDark = isDark)

        QuietaPage(
            title = stringResource(R.string.about),
            blurEnabled = blurEnabled,
            state = lazyListState,
            itemSpacing = 0.dp,
            bottomPadding = 28.dp,
            // Keep aurora visible; top bar still blurs on scroll via QuietaPage chrome.
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
            item(key = "hero-spacer") {
                // Fixed hero block (InstallerX sticky column + logoSpacer).
                AboutHero(
                    isDark = isDark,
                    topPadding = 56.dp,
                    versionText = versionInfoText(),
                )
            }

            item(key = "about-content") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = safeInsets.calculateStartPadding(layoutDirection),
                            end = safeInsets.calculateEndPadding(layoutDirection),
                            bottom = safeInsets.calculateBottomPadding(),
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

@Composable
private fun versionInfoText(): String {
    val level = if (BuildConfig.DEBUG) "调试版" else "正式版"
    return "$level ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
}

@Composable
private fun AboutHero(
    isDark: Boolean,
    topPadding: androidx.compose.ui.unit.Dp,
    versionText: String,
) {
    val context = LocalContext.current
    // Adaptive icon XML in mipmap-anydpi-v26 cannot be loaded via painterResource.
    val iconBitmap = androidx.compose.runtime.remember(context) {
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
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = null,
                modifier = Modifier.size(88.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 35.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) AboutTitleDark else AboutTitleLight,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = versionText,
            fontSize = 14.sp,
            color = if (isDark) AboutVersionDark else AboutVersionLight,
            textAlign = TextAlign.Center,
        )
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
    val surface = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)
    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        cornerRadius = 16.dp,
        colors = MiuixCardDefaults.defaultColors(
            color = surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        AboutNavRow(
            title = stringResource(R.string.about_source),
            subtitle = sourceSubtitle,
            onClick = onSourceClick,
        )
        AboutNavRow(
            title = stringResource(R.string.about_licenses),
            subtitle = licenseSubtitle,
            onClick = onLicenseClick,
        )
        AboutNavRow(
            title = stringResource(R.string.about_check_update),
            subtitle = updateSubtitle,
            onClick = onUpdateClick,
            trailing = {
                if (updateChecking) {
                    InfiniteProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        releaseUrl?.let { url ->
            AboutNavRow(
                title = "打开 Release 页",
                subtitle = url,
                onClick = { onOpenRelease(url) },
            )
        }
    }
}

@Composable
private fun AboutNavRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
