package app.quieta.core.privilege

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import app.quieta.core.model.AppChannels

object InstalledApps {

    /**
     * User-facing installed apps (non-system first). Uses QUERY_ALL_PACKAGES when granted.
     */
    fun load(context: Context): List<AppChannels> {
        val pm = context.packageManager
        val flags = PackageManager.GET_META_DATA
        val packages = pm.getInstalledPackages(flags)
        return packages
            .asSequence()
            .filter { it.packageName != context.packageName }
            .mapNotNull { pkg ->
                val info = pkg.applicationInfo ?: return@mapNotNull null
                val label = runCatching {
                    pm.getApplicationLabel(info).toString()
                }.getOrDefault(pkg.packageName)
                val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                Triple(pkg.packageName, label, isSystem)
            }
            .sortedWith(compareBy({ it.third }, { it.second.lowercase() }))
            .map { (pkg, label, isSystem) ->
                AppChannels(
                    packageName = pkg,
                    appLabel = label,
                    channels = emptyList(),
                    isSystem = isSystem,
                )
            }
            .toList()
    }
}
