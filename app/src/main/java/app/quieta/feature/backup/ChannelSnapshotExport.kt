package app.quieta.feature.backup

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import app.quieta.core.model.AppChannels
import app.quieta.core.repo.ChannelSnapshotJson
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Writes channel-snapshot JSON to cache and builds a share chooser Intent. */
object ChannelSnapshotExport {

    fun buildShareIntent(context: Context, apps: List<AppChannels>): Intent {
        require(apps.isNotEmpty()) { "apps is empty" }
        val json = ChannelSnapshotJson.encode(
            apps = apps,
            exportedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            source = ChannelSnapshotJson.Source(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
            ),
        )
        val dir = File(context.cacheDir, "channel_snapshots").apply { mkdirs() }
        val stamp = DateTimeFormatter.ofPattern("yyMMdd-HHmm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        val file = File(dir, "quieta-channels-$stamp-${apps.size}.json")
        file.writeText(json)
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_SUBJECT,
                "Quieta channels apps=${ChannelSnapshotJson.countApps(apps)} ch=${ChannelSnapshotJson.countChannels(apps)}",
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "导出渠道设置")
    }

    fun summarize(apps: List<AppChannels>): String =
        "应用 ${ChannelSnapshotJson.countApps(apps)} · 渠道 ${ChannelSnapshotJson.countChannels(apps)}"
}
