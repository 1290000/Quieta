// SPDX-License-Identifier: GPL-3.0-only
// Adapted from InstallerX Revived ui/theme/Backdrop.kt (2026 contributors).
package app.quieta.ui.glass

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported

/** A separate, lens-free backdrop for the scrolling page's top bar. */
@Composable
fun rememberPageTopBarBackdrop(enabled: Boolean): LayerBackdrop? {
    if (!enabled || Build.VERSION.SDK_INT < 33 || !isRenderEffectSupported()) return null
    val background = MaterialTheme.colorScheme.background
    return rememberLayerBackdrop {
        drawRect(background)
        drawContent()
    }
}

/** InstallerX's 25px blur and 80% surface tint, with an opaque fallback in the caller. */
@Composable
fun Modifier.pageTopBarBlur(backdrop: LayerBackdrop?): Modifier {
    if (backdrop == null) return this
    return textureBlur(
        backdrop = backdrop,
        shape = RectangleShape,
        blurRadius = 25f,
        colors = BlurColors(
            blendColors = listOf(BlendColorEntry(MaterialTheme.colorScheme.background.copy(alpha = 0.8f))),
        ),
    )
}
