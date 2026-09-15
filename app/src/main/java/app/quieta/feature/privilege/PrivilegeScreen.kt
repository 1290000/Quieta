// SPDX-License-Identifier: GPL-3.0-only
// Layout adapted from InstallerX Revived MiuixPrivPage (2026 contributors).
package app.quieta.feature.privilege

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.quieta.R
import app.quieta.core.settings.PreferredAuthorizer
import app.quieta.ui.component.QuietaPage
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Global authorizer selection, with availability supplied by the shared home state. */
@Composable
fun PrivilegeScreen(
    selected: PreferredAuthorizer,
    rootAvailable: Boolean,
    rootLabel: String,
    rootDescription: String,
    shizukuAvailable: Boolean,
    shizukuAuthorized: Boolean,
    dhizukuAvailable: Boolean,
    onBack: () -> Unit,
    onSelect: (PreferredAuthorizer) -> Unit,
    modifier: Modifier = Modifier,
    blurEnabled: Boolean = true,
) {
    BackHandler(onBack = onBack)
    QuietaPage(
        title = stringResource(R.string.home_stat_authorizers),
        modifier = modifier,
        blurEnabled = blurEnabled,
        horizontalPadding = 0.dp,
        topPadding = 0.dp,
        bottomPadding = 12.dp,
        itemSpacing = 0.dp,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
            }
        },
    ) {
        item(key = "availability_tip") { TipCard(stringResource(R.string.privilege_availability_tip)) }
        item(key = "scope_tip") { TipCard(stringResource(R.string.privilege_scope_tip)) }
        item(key = "authorizers") {
            SmallTitle(text = stringResource(R.string.privilege_authorizer))
            Card(
                modifier = Modifier.padding(horizontal = 12.dp).selectableGroup(),
            ) {
                AuthorizerRow(
                    title = stringResource(R.string.privilege_none),
                    description = stringResource(R.string.privilege_none_description),
                    selected = selected == PreferredAuthorizer.NONE,
                    onClick = { onSelect(PreferredAuthorizer.NONE) },
                )
                AuthorizerRow(
                    title = if (rootAvailable) "ROOT ($rootLabel)" else "ROOT",
                    description = rootDescription.ifBlank { stringResource(R.string.privilege_unavailable) },
                    selected = selected == PreferredAuthorizer.ROOT,
                    onClick = { onSelect(PreferredAuthorizer.ROOT) },
                )
                AuthorizerRow(
                    title = "Shizuku",
                    description = stringResource(when {
                        shizukuAuthorized -> R.string.privilege_running
                        shizukuAvailable -> R.string.privilege_shizuku_authorize
                        else -> R.string.privilege_shizuku_start
                    }),
                    selected = selected == PreferredAuthorizer.SHIZUKU,
                    onClick = { onSelect(PreferredAuthorizer.SHIZUKU) },
                )
                AuthorizerRow(
                    title = "Dhizuku",
                    description = stringResource(if (dhizukuAvailable) R.string.privilege_running else R.string.privilege_dhizuku_activate),
                    selected = selected == PreferredAuthorizer.DHIZUKU,
                    onClick = { onSelect(PreferredAuthorizer.DHIZUKU) },
                )
                AuthorizerRow(
                    title = stringResource(R.string.privilege_auto),
                    description = stringResource(R.string.privilege_auto_description),
                    selected = selected == PreferredAuthorizer.AUTO,
                    onClick = { onSelect(PreferredAuthorizer.AUTO) },
                )
            }
        }
    }
}

@Composable
private fun AuthorizerRow(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    BasicComponent(
        modifier = Modifier.semantics { this.selected = selected },
        title = title,
        summary = description,
        role = Role.RadioButton,
        onClick = onClick,
        endActions = {
            Checkbox(
                state = ToggleableState(selected),
                onClick = null,
                modifier = Modifier.clearAndSetSemantics { },
            )
        },
    )
}

@Composable
private fun TipCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.primary.copy(alpha = 0.2f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
