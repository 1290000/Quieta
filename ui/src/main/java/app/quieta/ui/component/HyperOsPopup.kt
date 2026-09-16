// SPDX-License-Identifier: GPL-3.0-only
// Compact popup list: width/height follow content (HyperOS 文件管理器 sort menu).
package app.quieta.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * HyperOS-style compact menu (文件管理器 sort popup).
 * Width = widest row (IntrinsicSize.Max), never a fixed slab.
 * Height wraps content. Tap outside / back dismisses.
 */
@Composable
fun HyperOsPopup(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 280.dp,
    topPadding: Dp = 96.dp,
    endPadding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onDismissRequest) {
                    detectTapGestures(onTap = { onDismissRequest() })
                },
            contentAlignment = Alignment.TopEnd,
        ) {
            Card(
                modifier = modifier
                    .padding(top = topPadding, end = endPadding)
                    .widthIn(max = maxContentWidth)
                    .width(IntrinsicSize.Max)
                    .pointerInput(Unit) { detectTapGestures() }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                cornerRadius = 22.dp,
                insideMargin = PaddingValues(vertical = 4.dp),
            ) {
                Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun HyperOsPopupDivider() {
    HorizontalDivider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        thickness = 0.5.dp,
        color = MiuixTheme.colorScheme.dividerLine,
    )
}

/**
 * Menu row: 16sp label, compact 12dp vertical padding.
 * Column width follows the longest sibling so checks line up without a wide empty tail.
 */
@Composable
fun HyperOsPopupRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showCheck: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) MiuixTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showCheck) {
            Box(
                modifier = Modifier.width(20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
