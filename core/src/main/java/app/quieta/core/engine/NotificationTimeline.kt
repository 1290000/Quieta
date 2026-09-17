package app.quieta.core.engine

import java.util.UUID

/**
 * Privacy-preserving notification summary.
 * Still no title, text, extras, or notification key — only identity + timing + volume.
 */
data class NotificationTimelineEntry(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    val appLabel: String = "",
    val channelId: String,
    val channelName: String = "",
    /** First event in the merged burst. */
    val firstAt: Long,
    /** Last event in the merged burst; used for sorting. */
    val lastAt: Long = firstAt,
    val count: Int = 1,
    /** Android channel importance at capture (0..5). */
    val importance: Int = -1,
) {
    val timestamp: Long get() = lastAt
}

internal object NotificationTimelineReducer {
    const val MAX_ENTRIES = 200
    const val RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000
    /** 1-minute burst window — finer than the old 5-minute merge. */
    const val MERGE_WINDOW_MILLIS = 60L * 1000

    fun append(
        previous: List<NotificationTimelineEntry>,
        packageName: String,
        appLabel: String,
        channelId: String,
        channelName: String,
        timestamp: Long,
        importance: Int,
    ): List<NotificationTimelineEntry> {
        val cutoff = timestamp - RETENTION_MILLIS
        val retained = previous.filter { it.lastAt >= cutoff }
        val latestIndex = retained.indexOfFirst {
            it.packageName == packageName &&
                it.channelId == channelId &&
                timestamp - it.lastAt in 0..MERGE_WINDOW_MILLIS
        }
        val next = if (latestIndex >= 0) {
            retained.toMutableList().also { entries ->
                val old = entries[latestIndex]
                entries[latestIndex] = old.copy(
                    appLabel = appLabel.ifBlank { old.appLabel },
                    channelName = channelName.ifBlank { old.channelName },
                    lastAt = timestamp,
                    firstAt = minOf(old.firstAt, timestamp),
                    count = old.count + 1,
                    importance = if (importance >= 0) importance else old.importance,
                )
            }
        } else {
            listOf(
                NotificationTimelineEntry(
                    packageName = packageName,
                    appLabel = appLabel,
                    channelId = channelId,
                    channelName = channelName,
                    firstAt = timestamp,
                    lastAt = timestamp,
                    importance = importance,
                ),
            ) + retained
        }
        return next.sortedByDescending { it.lastAt }.take(MAX_ENTRIES)
    }
}
