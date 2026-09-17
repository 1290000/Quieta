package app.quieta.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTimelineReducerTest {
    @Test fun mergesSameChannelWithinOneMinute() {
        val first = NotificationTimelineReducer.append(
            emptyList(), "a", "App", "news", "News", 1_000_000L, 3, soundEnabled = true, vibrationEnabled = false,
        )
        val second = NotificationTimelineReducer.append(
            first, "a", "App", "news", "News", 1_030_000L, 3, soundEnabled = true, vibrationEnabled = false,
        )

        assertEquals(1, second.size)
        val merged = second.single()
        assertEquals(2, merged.count)
        assertEquals(1_000_000L, merged.firstAt)
        assertEquals(1_030_000L, merged.lastAt)
        assertEquals("News", merged.channelName)
    }

    @Test fun separatesChannelsAndEventsOutsideWindow() {
        val first = NotificationTimelineReducer.append(
            emptyList(), "a", "App", "news", "News", 1_000_000L, 3, soundEnabled = true, vibrationEnabled = false,
        )
        val second = NotificationTimelineReducer.append(
            first, "a", "App", "chat", "Chat", 1_030_000L, 4, soundEnabled = true, vibrationEnabled = false,
        )
        val third = NotificationTimelineReducer.append(
            second, "a", "App", "news", "News",
            1_000_000L + NotificationTimelineReducer.MERGE_WINDOW_MILLIS + 1,
            3, soundEnabled = true, vibrationEnabled = false,
        )

        assertEquals(3, third.size)
        assertTrue(third.all { it.count == 1 })
    }

    @Test fun dropsExpiredEntriesAndCapsHistory() {
        val now = 10_000_000L
        val old = NotificationTimelineEntry(
            packageName = "old",
            channelId = "x",
            firstAt = now - NotificationTimelineReducer.RETENTION_MILLIS - 1,
            lastAt = now - NotificationTimelineReducer.RETENTION_MILLIS - 1,
        )
        val retained = NotificationTimelineReducer.append(
            listOf(old), "new", "New", "x", "X", now, 1, soundEnabled = true, vibrationEnabled = false,
        )
        assertTrue(retained.none { it.packageName == "old" })

        val many = (0 until NotificationTimelineReducer.MAX_ENTRIES).map {
            NotificationTimelineEntry(
                packageName = "p$it",
                channelId = "c",
                firstAt = now - it - 1,
                lastAt = now - it - 1,
            )
        }
        val capped = NotificationTimelineReducer.append(
            many, "last", "Last", "c", "C", now, 2, soundEnabled = true, vibrationEnabled = false,
        )
        assertEquals(NotificationTimelineReducer.MAX_ENTRIES, capped.size)
        assertEquals("last", capped.first().packageName)
        assertEquals("Last", capped.first().appLabel)
    }
}
