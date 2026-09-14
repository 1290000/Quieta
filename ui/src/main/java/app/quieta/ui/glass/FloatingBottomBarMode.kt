package app.quieta.ui.glass

/** Bottom bar material. Phase 1: selection + stub rendering only. */
enum class FloatingBottomBarMode {
    LiquidGlass,
    Blur,
    None,
}

fun resolveBottomBarMode(
    blurEnabled: Boolean,
    liquidGlassSupported: Boolean,
): FloatingBottomBarMode = when {
    !blurEnabled -> FloatingBottomBarMode.None
    liquidGlassSupported -> FloatingBottomBarMode.LiquidGlass
    else -> FloatingBottomBarMode.Blur
}
