package app.quieta.ui.glass

import android.os.Build
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported

enum class FloatingBottomBarMode {
    LiquidGlass,
    Blur,
    None,
}

/**
 * True when AGSL / miuix blur is safe on this device.
 * Emulators often SIGSEGV in RenderThread with RuntimeShader.
 */
fun isLiquidGlassSafe(): Boolean {
    if (Build.VERSION.SDK_INT < 33) return false
    if (isProbablyEmulator()) return false
    return runCatching { isRuntimeShaderSupported() }.getOrDefault(false)
}

fun isProbablyEmulator(): Boolean {
    if (Build.SUPPORTED_ABIS.any { it.contains("x86", ignoreCase = true) }) return true
    val fp = Build.FINGERPRINT.lowercase()
    val model = Build.MODEL.lowercase()
    val product = Build.PRODUCT.lowercase()
    val manufacturer = Build.MANUFACTURER.lowercase()
    val hardware = Build.HARDWARE.lowercase()
    return fp.contains("generic") ||
        fp.contains("emulator") ||
        fp.contains("sdk_gphone") ||
        model.contains("emulator") ||
        model.contains("mumu") ||
        product.contains("sdk") ||
        manufacturer.contains("genymotion") ||
        hardware.contains("goldfish") ||
        hardware.contains("ranchu") ||
        hardware.contains("vbox")
}

fun resolveBottomBarMode(
    blurEnabled: Boolean,
    liquidGlassSupported: Boolean,
): FloatingBottomBarMode {
    // HyperOS (K40s / munch) SIGSEGV in RenderThread with miuix drawBackdrop+lens
    // (stack overflow in RuntimeShader). Keep solid/blur capsule; drag+scale still work.
    val allowShader = false
    return when {
        !blurEnabled -> FloatingBottomBarMode.None
        allowShader && liquidGlassSupported && isLiquidGlassSafe() -> FloatingBottomBarMode.LiquidGlass
        else -> FloatingBottomBarMode.None
    }
}
