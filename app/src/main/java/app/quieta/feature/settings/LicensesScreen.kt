package app.quieta.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.quieta.R
import app.quieta.ui.component.QuietaPage
import app.quieta.ui.component.PressableCard

private data class OssLib(
    val name: String,
    val author: String,
    val license: String,
    val url: String,
    val version: String? = null,
)

/**
 * Static OSS list — reliable on HyperOS; AboutLibraries plugin output not always present.
 */
private val ossLibs = listOf(
    OssLib("libsu (core / RootService)", "John Wu (topjohnwu)", "Apache-2.0", "https://github.com/topjohnwu/libsu", "6.0.0"),
    OssLib("miuix (Tilt / TopAppBar / overscroll / controls)", "compose-miuix-ui", "Apache-2.0", "https://github.com/compose-miuix-ui/miuix", "includeBuild"),
    OssLib("InstallerX Revived (cards / typography / page chrome / privilege layout / glass)", "wxxsfxyzm and contributors", "GPL-3.0-only", "https://github.com/wxxsfxyzm/InstallerX-Revived", "f6ffcd8"),
    // Code we ported from InstallerX also contains third-party lineage — list separately:
    OssLib("KernelSU (via InstallerX DampedDrag / pager)", "tiann", "GPL-3.0", "https://github.com/tiann/KernelSU"),
    OssLib("LibChecker (scan/cache architecture reference)", "LibChecker", "Apache-2.0", "https://github.com/LibChecker/LibChecker"),
    OssLib("Shizuku API", "RikkaApps", "Apache-2.0", "https://github.com/RikkaApps/Shizuku-API", "13.1.5"),
    OssLib("Dhizuku-API", "iamr0s", "Apache-2.0", "https://github.com/iamr0s/Dhizuku-API", "2.6.0"),
    OssLib("HiddenApiBypass", "LSPosed", "GPL-3.0", "https://github.com/LSPosed/AndroidHiddenApiBypass", "6.1"),
    OssLib("AndroidLiquidGlass", "Kyant0", "Apache-2.0", "https://github.com/Kyant0/AndroidLiquidGlass"),
    OssLib("Jetpack Compose", "Android Open Source Project", "Apache-2.0", "https://developer.android.com/jetpack/compose"),
    OssLib("AndroidX", "Android Open Source Project", "Apache-2.0", "https://github.com/androidx/androidx"),
)

@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    blurEnabled: Boolean = true,
) {
    val context = LocalContext.current
    QuietaPage(
        title = stringResource(R.string.about_licenses),
        modifier = modifier,
        blurEnabled = blurEnabled,
        bottomPadding = 24.dp,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
            }
        },
    ) {
        items(ossLibs, key = { it.url }) { lib ->
            PressableCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lib.url)))
                    }
                },
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(lib.name, style = MaterialTheme.typography.titleMedium)
                    Text(lib.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(
                            text = lib.license + (lib.version?.let { " · $it" } ?: ""),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}
