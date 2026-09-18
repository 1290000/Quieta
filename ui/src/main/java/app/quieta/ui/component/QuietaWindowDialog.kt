// SPDX-License-Identifier: GPL-3.0-only
// InstallerX Revived MiuixDialog / miuix WindowDialog alignment helpers.
package app.quieta.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Centered miuix dialog chrome matching InstallerX `WindowDialog` usage:
 * title + optional summary + content + bottom cancel / confirm row.
 *
 * Prefer this over raw `androidx.compose.ui.window.Dialog` + nested Cards.
 * Light pick menus stay on [HyperOsPopup].
 */
@Composable
fun QuietaWindowDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmPrimary: Boolean = true,
    cancelText: String = "取消",
    insideMargin: DpSize = DpSize(0.dp, 24.dp),
    content: @Composable ColumnScope.() -> Unit = {},
) {
    WindowDialog(
        show = show,
        modifier = modifier,
        title = title,
        summary = summary,
        insideMargin = insideMargin,
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
            Spacer(modifier = Modifier.height(12.dp))
            if (confirmText != null && onConfirm != null) {
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onConfirm,
                    text = confirmText,
                    colors = if (confirmPrimary) {
                        ButtonDefaults.textButtonColorsPrimary()
                    } else {
                        ButtonDefaults.textButtonColors()
                    },
                )
            }
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onDismissRequest,
                text = cancelText,
            )
        }
    }
}
