// SPDX-License-Identifier: GPL-3.0-only
// Adapted from InstallerX Revived MiuixHomePage / MiuixPrivPage (2026 contributors).
package app.quieta.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.quieta.ui.glass.pageTopBarBlur
import app.quieta.ui.glass.rememberPageTopBarBackdrop
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.utils.overScrollVertical

/** Shared collapsing chrome and edge spring; each page owns its scroll state. */
@Composable
fun QuietaPage(
    title: String,
    modifier: Modifier = Modifier,
    blurEnabled: Boolean = true,
    state: LazyListState = rememberLazyListState(),
    horizontalPadding: Dp = 12.dp,
    topPadding: Dp = 12.dp,
    bottomPadding: Dp = 110.dp,
    itemSpacing: Dp = 12.dp,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberPageTopBarBackdrop(blurEnabled)
    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                modifier = Modifier.pageTopBarBlur(backdrop),
                title = title,
                color = if (backdrop != null) Color.Transparent else MaterialTheme.colorScheme.background,
                titleColor = MaterialTheme.colorScheme.onBackground,
                largeTitleColor = MaterialTheme.colorScheme.onBackground,
                navigationIcon = {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                        navigationIcon()
                    }
                },
                actions = {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                        actions()
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection) + horizontalPadding,
                top = padding.calculateTopPadding() + topPadding,
                end = padding.calculateEndPadding(layoutDirection) + horizontalPadding,
                bottom = padding.calculateBottomPadding() + bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            overscrollEffect = null,
            content = content,
        )
    }
}
