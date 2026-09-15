package app.quieta.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.quieta.R

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
    OssLib("miuix (local source)", "compose-miuix-ui", "Apache-2.0", "https://github.com/compose-miuix-ui/miuix", "includeBuild"),
    OssLib("InstallerX Revived (reference)", "wxxsfxyzm", "GPL-3.0", "https://github.com/wxxsfxyzm/InstallerX-Revived"),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_licenses)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "第三方开源库；点击条目打开对应仓库。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(ossLibs, key = { it.url }) { lib ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lib.url)))
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(lib.name, style = MaterialTheme.typography.titleMedium)
                        Text(lib.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFE8F0FE),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text(
                                text = lib.license + (lib.version?.let { " · $it" } ?: ""),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF1A56A8),
                            )
                        }
                    }
                }
            }
        }
    }
}
