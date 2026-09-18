package app.quieta.core.repo

import app.quieta.core.model.AppChannels
import app.quieta.core.model.Channel
import app.quieta.core.model.ChannelImportance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelSnapshotJsonTest {

    private val inventory = listOf(
        AppChannels(
            packageName = "com.example.chat",
            appLabel = "聊天",
            channels = listOf(
                Channel("com.example.chat", "promo", "促销", ChannelImportance.DEFAULT),
                Channel("com.example.chat", "msg", "消息", ChannelImportance.HIGH),
            ),
            isSystem = false,
        ),
        AppChannels(
            packageName = "com.example.shop",
            appLabel = "购物",
            channels = listOf(
                Channel("com.example.shop", "deals", "优惠", ChannelImportance.LOW),
            ),
            isSystem = false,
        ),
    )

    @Test
    fun `encode decode roundtrip keeps importance and labels`() {
        val raw = ChannelSnapshotJson.encode(
            apps = inventory,
            exportedAt = "2026-09-18T00:00:00Z",
            source = ChannelSnapshotJson.Source(manufacturer = "Xiaomi", model = "K40s"),
        )
        assertTrue(raw.contains(ChannelSnapshotJson.KIND))
        val decoded = ChannelSnapshotJson.decode(raw)
        assertEquals(2, decoded.apps.size)
        assertEquals("聊天", decoded.apps[0].appLabel)
        assertEquals(ChannelImportance.DEFAULT, decoded.apps[0].channels[0].importance)
        assertEquals(ChannelImportance.HIGH, decoded.apps[0].channels[1].importance)
        assertEquals("K40s", decoded.source?.model)
        assertEquals("2026-09-18T00:00:00Z", decoded.exportedAt)
    }

    @Test
    fun `project empty keys keeps full inventory`() {
        assertEquals(inventory, ChannelSnapshotJson.project(inventory, emptySet()))
    }

    @Test
    fun `project selected keys keeps only matching channels`() {
        val keys = setOf(
            ChannelSnapshotJson.key("com.example.chat", "msg"),
            ChannelSnapshotJson.key("com.example.shop", "deals"),
        )
        val projected = ChannelSnapshotJson.project(inventory, keys)
        assertEquals(2, projected.size)
        assertEquals(listOf("msg"), projected[0].channels.map { it.id })
        assertEquals(listOf("deals"), projected[1].channels.map { it.id })
        assertEquals(2, ChannelSnapshotJson.countChannels(projected))
    }

    @Test
    fun `project drops apps with no selected channel`() {
        val keys = setOf(ChannelSnapshotJson.key("com.example.chat", "promo"))
        val projected = ChannelSnapshotJson.project(inventory, keys)
        assertEquals(1, projected.size)
        assertEquals("com.example.chat", projected[0].packageName)
        assertEquals(listOf("promo"), projected[0].channels.map { it.id })
    }

    @Test
    fun `decode rejects wrong kind`() {
        val raw = """{"kind":"quieta-rules","schemaVersion":1,"apps":[]}"""
        try {
            ChannelSnapshotJson.decode(raw)
            throw AssertionError("expected rejection")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
