package app.quieta.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quieta.R
import app.quieta.core.settings.PaletteStyle
import app.quieta.core.settings.PredictiveBackAnimation
import app.quieta.core.settings.PredictiveBackExitDirection
import app.quieta.core.settings.ThemeMode
import app.quieta.ui.component.QuietaPage
import app.quieta.ui.component.QuietaSwitch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference

/**
 * InstallerX MiuixThemeSettingsPage–aligned theme page (Quieta subset).
 * Selectors use miuix WindowSpinnerPreference (same as InstallerX theme pickers).
 */
@Composable
fun ThemeScreen(
    onBack: () -> Unit,
    blurEnabledForChrome: Boolean,
    onBlurEnabledChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val customColors by viewModel.customColors.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val paletteStyle by viewModel.paletteStyle.collectAsStateWithLifecycle()
    val pbAnimation by viewModel.predictiveBackAnimation.collectAsStateWithLifecycle()
    val pbExit by viewModel.predictiveBackExitDirection.collectAsStateWithLifecycle()

    val themeModes = remember { ThemeMode.entries }
    val themeItems = themeModes.map { DropdownItem(title = themeModeLabel(it)) }
    val palettes = remember { PaletteStyle.entries }
    val paletteItems = palettes.map { DropdownItem(title = paletteLabel(it)) }
    val pbAnims = remember { PredictiveBackAnimation.entries }
    val pbAnimItems = pbAnims.map { DropdownItem(title = pbAnimationLabel(it)) }
    val pbExits = remember { PredictiveBackExitDirection.entries }
    val pbExitItems = pbExits.map { DropdownItem(title = pbExitLabel(it)) }

    QuietaPage(
        title = stringResource(R.string.theme_settings),
        modifier = modifier,
        blurEnabled = blurEnabledForChrome,
        itemSpacing = 0.dp,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
            }
        },
    ) {
        item(key = "display-section") {
            SmallTitle(stringResource(R.string.theme_settings_display))
            SettingsGroupCard {
                WindowSpinnerPreference(
                    items = themeItems,
                    selectedIndex = themeModes.indexOf(themeMode).coerceAtLeast(0),
                    title = stringResource(R.string.theme_settings_theme_mode),
                    onSelectedIndexChange = { index ->
                        val mode = themeModes[index]
                        if (mode != themeMode) viewModel.setThemeMode(mode)
                    },
                )
                BasicComponent(
                    title = stringResource(R.string.theme_settings_use_blur),
                    summary = stringResource(R.string.theme_settings_use_blur_desc),
                    onClick = { onBlurEnabledChange(!blurEnabledForChrome) },
                    endActions = {
                        QuietaSwitch(checked = blurEnabledForChrome, onCheckedChange = onBlurEnabledChange)
                    },
                )
                BasicComponent(
                    title = stringResource(R.string.theme_settings_custom_colors),
                    summary = stringResource(R.string.theme_settings_custom_colors_desc),
                    onClick = { viewModel.setCustomColors(!customColors) },
                    endActions = {
                        QuietaSwitch(checked = customColors, onCheckedChange = viewModel::setCustomColors)
                    },
                )
                AnimatedVisibility(
                    visible = customColors,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column {
                        BasicComponent(
                            title = stringResource(R.string.theme_settings_dynamic_color),
                            summary = stringResource(R.string.theme_settings_dynamic_color_desc),
                            onClick = { viewModel.setDynamicColor(!dynamicColor) },
                            endActions = {
                                QuietaSwitch(checked = dynamicColor, onCheckedChange = viewModel::setDynamicColor)
                            },
                        )
                        WindowSpinnerPreference(
                            items = paletteItems,
                            selectedIndex = palettes.indexOf(paletteStyle).coerceAtLeast(0),
                            title = stringResource(R.string.theme_settings_palette),
                            onSelectedIndexChange = { index ->
                                val style = palettes[index]
                                if (style != paletteStyle) viewModel.setPaletteStyle(style)
                            },
                        )
                    }
                }
            }
        }

        item(key = "predictive-back") {
            SmallTitle(stringResource(R.string.theme_settings_predictive_back))
            SettingsGroupCard {
                WindowSpinnerPreference(
                    items = pbAnimItems,
                    selectedIndex = pbAnims.indexOf(pbAnimation).coerceAtLeast(0),
                    title = stringResource(R.string.theme_settings_pb_animation),
                    onSelectedIndexChange = { index ->
                        val anim = pbAnims[index]
                        if (anim != pbAnimation) viewModel.setPredictiveBackAnimation(anim)
                    },
                )
                AnimatedVisibility(
                    visible = pbAnimation == PredictiveBackAnimation.SCALE ||
                        pbAnimation == PredictiveBackAnimation.CLASSIC ||
                        pbAnimation == PredictiveBackAnimation.MIUIX,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    WindowSpinnerPreference(
                        items = pbExitItems,
                        selectedIndex = pbExits.indexOf(pbExit).coerceAtLeast(0),
                        title = stringResource(R.string.theme_settings_pb_exit),
                        onSelectedIndexChange = { index ->
                            val dir = pbExits[index]
                            if (dir != pbExit) viewModel.setPredictiveBackExitDirection(dir)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp, start = 12.dp, end = 12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column { content() }
    }
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.theme_mode_system)
    ThemeMode.LIGHT -> stringResource(R.string.theme_mode_light)
    ThemeMode.DARK -> stringResource(R.string.theme_mode_dark)
}

@Composable
private fun paletteLabel(style: PaletteStyle): String = when (style) {
    PaletteStyle.TonalSpot -> "TonalSpot"
    PaletteStyle.Vibrant -> "Vibrant"
    PaletteStyle.Expressive -> "Expressive"
    PaletteStyle.Spritz -> "Spritz"
    PaletteStyle.FruitSalad -> "FruitSalad"
    PaletteStyle.Rainbow -> "Rainbow"
    PaletteStyle.Monochrome -> "Monochrome"
}

@Composable
private fun pbAnimationLabel(value: PredictiveBackAnimation): String = when (value) {
    PredictiveBackAnimation.NONE -> "无"
    PredictiveBackAnimation.AOSP -> "AOSP"
    PredictiveBackAnimation.MIUIX -> "Miuix"
    PredictiveBackAnimation.SCALE -> "缩放"
    PredictiveBackAnimation.CLASSIC -> "Classic"
}

@Composable
private fun pbExitLabel(value: PredictiveBackExitDirection): String = when (value) {
    PredictiveBackExitDirection.FOLLOW_GESTURE -> "跟随手势"
    PredictiveBackExitDirection.ALWAYS_RIGHT -> "始终向右"
    PredictiveBackExitDirection.ALWAYS_LEFT -> "始终向左"
}


