package app.quieta.core.engine

import java.util.UUID

/** A privacy-preserving notification summary: no title, text, extras, or notification key. */
data class NotificationTimelineEntry(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    val channelId: String,
    val timestamp: Long,
    val count: Int = 1,
)

internal object NotificationTimelineReducer {
    const val MAX_ENTRIES = 200
    const val RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000
    const val MERGE_WINDOW_MILLIS = 5L * 60 * 1000

    fun append(
        previous: List<NotificationTimelineEntry>,
        packageName: String,
        channelId: String,
        timestamp: Long,
    ): List<NotificationTimelineEntry> {
        val cutoff = timestamp - RETENTION_MILLIS
        val retained = previous.filter { it.timestamp >= cutoff }
        val latestIndex = retained.indexOfFirst {
            it.packageName == packageName &&
                it.channelId == channelId &&
                timestamp - it.timestamp in 0..MERGE_WINDOW_MILLIS
        }
        val next = if (latestIndex >= 0) {
            retained.toMutableList().also { entries ->
                val old = entries[latestIndex]
                entries[latestIndex] = old.copy(
                    timestamp = timestamp,
                    count = old.count + 1,
                )
            }
        } else {
            listOf(NotificationTimelineEntry(
                packageName = packageName,
                channelId = channelId,
                timestamp = timestamp,
            )) + retained
        }
        return next.sortedByDescending { it.timestamp }.take(MAX_ENTRIES)
    }
}
