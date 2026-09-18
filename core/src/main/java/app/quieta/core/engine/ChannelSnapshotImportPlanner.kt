package app.quieta.core.engine

import app.quieta.core.model.AppChannels
import app.quieta.core.model.ChannelImportance
import app.quieta.core.repo.ChannelSnapshotJson

data class SnapshotImportItem(
    val packageName: String,
    val appLabel: String,
    val channelId: String,
    val channelName: String,
    val currentImportance: ChannelImportance,
    val targetImportance: ChannelImportance,
    val isSystem: Boolean,
)

data class SnapshotSkipItem(
    val packageName: String,
    val appLabel: String,
    val channelId: String?,
    val channelName: String?,
    val reason: String,
)

data class SnapshotImportPlan(
    val fileApps: Int = 0,
    val fileChannels: Int = 0,
    val apply: List<SnapshotImportItem> = emptyList(),
    val skips: List<SnapshotSkipItem> = emptyList(),
    val sameCount: Int = 0,
    val appMissingCount: Int = 0,
    val channelMissingCount: Int = 0,
    val systemHeldCount: Int = 0,
) {
    val applyCount: Int get() = apply.size
}

/**
 * Dry-run comparator: package + channel id only. No name fuzzy matching.
 * System apps are held by default unless [includeSystem] is true.
 */
object ChannelSnapshotImportPlanner {

    fun plan(
        snapshot: ChannelSnapshotJson.Snapshot,
        local: List<AppChannels>,
        includeSystem: Boolean = false,
    ): SnapshotImportPlan {
        val localByPkg = local.associateBy { it.packageName }
        val apply = mutableListOf<SnapshotImportItem>()
        val skips = mutableListOf<SnapshotSkipItem>()
        var same = 0
        var appMissing = 0
        var channelMissing = 0
        var systemHeld = 0
        var fileChannels = 0

        snapshot.apps.forEach { snapApp ->
            val localApp = localByPkg[snapApp.packageName]
            if (localApp == null) {
                appMissing++
                snapApp.channels.forEach { ch ->
                    fileChannels++
                    skips += SnapshotSkipItem(
                        packageName = snapApp.packageName,
                        appLabel = snapApp.appLabel,
                        channelId = ch.id,
                        channelName = ch.name,
                        reason = "应用未安装或不在盘点中",
                    )
                }
                return@forEach
            }
            val localChById = localApp.channels.associateBy { it.id }
            val label = localApp.appLabel.ifEmpty { snapApp.appLabel }
            snapApp.channels.forEach { ch ->
                fileChannels++
                val localCh = localChById[ch.id]
                when {
                    localCh == null -> {
                        channelMissing++
                        skips += SnapshotSkipItem(
                            packageName = snapApp.packageName,
                            appLabel = label,
                            channelId = ch.id,
                            channelName = ch.name,
                            reason = "本机无此渠道 id",
                        )
                    }
                    !includeSystem && localApp.isSystem -> {
                        systemHeld++
                        skips += SnapshotSkipItem(
                            packageName = snapApp.packageName,
                            appLabel = label,
                            channelId = ch.id,
                            channelName = localCh.name.ifEmpty { ch.name },
                            reason = "系统应用（默认不改）",
                        )
                    }
                    localCh.importance == ch.importance -> {
                        same++
                    }
                    else -> {
                        apply += SnapshotImportItem(
                            packageName = snapApp.packageName,
                            appLabel = label,
                            channelId = ch.id,
                            channelName = localCh.name.ifEmpty { ch.name },
                            currentImportance = localCh.importance,
                            targetImportance = ch.importance,
                            isSystem = localApp.isSystem,
                        )
                    }
                }
            }
        }

        return SnapshotImportPlan(
            fileApps = snapshot.apps.size,
            fileChannels = fileChannels,
            apply = apply,
            skips = skips,
            sameCount = same,
            appMissingCount = appMissing,
            channelMissingCount = channelMissing,
            systemHeldCount = systemHeld,
        )
    }
}
