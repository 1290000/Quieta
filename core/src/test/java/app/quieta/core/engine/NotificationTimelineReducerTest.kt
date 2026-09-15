package app.quieta.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTimelineReducerTest {
    @Test fun mergesSameChannelWithinFiveMinutes() {
        val first = NotificationTimelineReducer.append(emptyList(), "a", "news", 1_000_000L)
        val second = NotificationTimelineReducer.append(first, "a", "news", 1_100_000L)

        assertEquals(1, second.size)
        assertEquals(2, second.single().count)
        assertEquals(1_100_000L, second.single().timestamp)
    }

    @Test fun separatesChannelsAndEventsOutsideWindow() {
        val first = NotificationTimelineReducer.append(emptyList(), "a", "news", 1_000_000L)
        val second = NotificationTimelineReducer.append(first, "a", "chat", 1_100_000L)
        val third = NotificationTimelineReducer.append(second, "a", "news", 1_000_000L + NotificationTimelineReducer.MERGE_WINDOW_MILLIS + 1)

        assertEquals(3, third.size)
        assertTrue(third.all { it.count == 1 })
    }

    @Test fun dropsExpiredEntriesAndCapsHistory() {
        val now = 10_000_000L
        val old = NotificationTimelineEntry(packageName = "old", channelId = "x", timestamp = now - NotificationTimelineReducer.RETENTION_MILLIS - 1)
        val retained = NotificationTimelineReducer.append(listOf(old), "new", "x", now)
        assertTrue(retained.none { it.packageName == "old" })

        val many = (0 until NotificationTimelineReducer.MAX_ENTRIES).map {
            NotificationTimelineEntry(packageName = "p$it", channelId = "c", timestamp = now - it - 1)
        }
        val capped = NotificationTimelineReducer.append(many, "last", "c", now)
        assertEquals(NotificationTimelineReducer.MAX_ENTRIES, capped.size)
        assertEquals("last", capped.first().packageName)
    }
}
