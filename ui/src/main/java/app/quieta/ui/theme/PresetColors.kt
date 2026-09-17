// SPDX-License-Identifier: GPL-3.0-only
// Ported from InstallerX Revived ui/theme/material/PresetColors.kt
package app.quieta.ui.theme

import androidx.compose.ui.graphics.Color

data class RawColor(val key: String, val color: Color)

val PresetColors = listOf(
    RawColor("default", Color(0xFF4A672D)),
    RawColor("pink", Color(0xFFB94073)),
    RawColor("red", Color(0xFFBA1A1A)),
    RawColor("orange", Color(0xFF944A00)),
    RawColor("amber", Color(0xFF8C5300)),
    RawColor("yellow", Color(0xFF795900)),
    RawColor("lime", Color(0xFF5E6400)),
    RawColor("green", Color(0xFF006D39)),
    RawColor("cyan", Color(0xFF006A64)),
    RawColor("teal", Color(0xFF006874)),
    RawColor("light_blue", Color(0xFF00639B)),
    RawColor("blue", Color(0xFF335BBC)),
    RawColor("indigo", Color(0xFF5355A9)),
    RawColor("purple", Color(0xFF6750A4)),
    RawColor("deep_purple", Color(0xFF7E42A4)),
    RawColor("blue_grey", Color(0xFF575D7E)),
    RawColor("brown", Color(0xFF7D524A)),
    RawColor("grey", Color(0xFF5F6162)),
)

fun presetColorName(key: String): String = when (key) {
    "default" -> "默认"
    "pink" -> "粉红色"
    "red" -> "红色"
    "orange" -> "橙色"
    "amber" -> "琥珀色"
    "yellow" -> "黄色"
    "lime" -> "酸橙色"
    "green" -> "绿色"
    "cyan" -> "青色"
    "teal" -> "蓝绿色"
    "light_blue" -> "浅蓝色"
    "blue" -> "蓝色"
    "indigo" -> "靛蓝色"
    "purple" -> "紫色"
    "deep_purple" -> "深紫色"
    "blue_grey" -> "蓝灰色"
    "brown" -> "棕色"
    "grey" -> "灰色"
    else -> key
}
