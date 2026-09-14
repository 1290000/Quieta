package app.quieta.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Resolves bottom-bar glass support for the current device. */
@Composable
fun rememberLiquidGlassSupported(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        android.os.Build.VERSION.SDK_INT >= 33
    }
}
