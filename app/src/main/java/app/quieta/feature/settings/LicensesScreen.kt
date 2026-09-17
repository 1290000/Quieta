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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.quieta.R
import app.quieta.ui.component.QuietaPage
import app.quieta.ui.component.PressableCard
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries

private data class OssLib(
    val name: String,
    val author: String,
    val license: String,
    val url: String,
    val version: String? = null,
)

/** Source ports and architecture references that cannot be discovered from Gradle metadata. */
private val supplementalOssLibs = listOf(
    OssLib("miuix (Tilt / squircle path / TopAppBar / overscroll / controls)", "compose-miuix-ui", "Apache-2.0", "https://github.com/compose-miuix-ui/miuix", "includeBuild"),
    OssLib("InstallerX Revived (cards / typography / page chrome / privilege layout / glass)", "wxxsfxyzm and contributors", "GPL-3.0-only", "https://github.com/wxxsfxyzm/InstallerX-Revived", "f6ffcd8"),
    OssLib("KernelSU (via InstallerX DampedDrag / pager)", "tiann", "GPL-3.0", "https://github.com/tiann/KernelSU"),
    OssLib("LibChecker (scan/cache architecture reference)", "LibChecker", "Apache-2.0", "https://github.com/LibChecker/LibChecker"),
    OssLib("AndroidLiquidGlass", "Kyant0", "Apache-2.0", "https://github.com/Kyant0/AndroidLiquidGlass"),
)

@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    blurEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val generatedLibraries by produceLibraries(R.raw.aboutlibraries)
    // InstallerX pattern: do not paint a partial list. Wait until AboutLibraries is ready,
    // then insert the full sorted set once — avoids the "few cards then a flash of many" jump.
    val ossLibs = remember(generatedLibraries) {
        val libraries = generatedLibraries ?: return@remember emptyList()
        val autoLibs = libraries.libraries.map(::toOssLib)
        (autoLibs + supplementalOssLibs)
            .distinctBy { it.url }
            .sortedBy { it.name.lowercase() }
    }
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
        if (generatedLibraries == null) {
            item(key = "licenses-loading") {
                Text(
                    text = "正在加载开源许可…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                )
            }
        } else {
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
}

private fun toOssLib(library: Library): OssLib {
    val artifactCoordinates = library.uniqueId.split(':')
    val artifactPage = artifactCoordinates.takeIf { it.size >= 2 }?.let {
        "https://central.sonatype.com/artifact/${it[0]}/${it[1]}"
    }
    val url = listOfNotNull(
        library.website,
        library.scm?.url,
        library.organization?.url,
        library.developers.firstNotNullOfOrNull { it.organisationUrl },
        library.licenses.firstNotNullOfOrNull { it.url },
        artifactPage,
    ).firstOrNull { candidate -> candidate.startsWith("https://") || candidate.startsWith("http://") }
        ?: "https://central.sonatype.com/search?q=${library.artifactId}"
    val author = library.organization?.name
        ?: library.developers.mapNotNull { developer -> developer.name }
            .filter { name -> name.isNotBlank() }
            .joinToString()
            .ifBlank { "Open-source contributors" }
    val license = library.licenses
        .mapNotNull { entry -> entry.spdxId ?: entry.name }
        .filter { name -> name.isNotBlank() }
        .joinToString()
        .ifBlank { "License metadata unavailable" }
    return OssLib(
        name = library.name.ifBlank { library.artifactId },
        author = author,
        license = license,
        url = url,
        version = library.artifactVersion,
    )
}
