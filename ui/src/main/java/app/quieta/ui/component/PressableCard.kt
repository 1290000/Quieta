// SPDX-License-Identifier: GPL-3.0-only
// Adapted from InstallerX Revived MiuixHomePage (2026 contributors).
package app.quieta.ui.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** Position-aware tilt and press tint shared by actionable standalone cards. */
@Composable
fun PressableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    cornerRadius: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        cornerRadius = cornerRadius,
        colors = CardDefaults.defaultColors(
            color = color,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        onClick = onClick,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Tilt,
        content = content,
    )
}
