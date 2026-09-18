package app.quieta.core.engine

import app.quieta.core.model.AppChannels
import app.quieta.core.model.Channel
import app.quieta.core.model.ChannelImportance
import app.quieta.core.repo.ChannelSnapshotJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelSnapshotImportPlannerTest {

    private val local = listOf(
        AppChannels(
            packageName = "com.example.chat",
            appLabel = "聊天",
            channels = listOf(
                Channel("com.example.chat", "promo", "促销", ChannelImportance.DEFAULT),
                Channel("com.example.chat", "msg", "消息", ChannelImportance.HIGH),
                Channel("com.example.chat", "local_only", "仅本机", ChannelImportance.LOW),
            ),
            isSystem = false,
        ),
        AppChannels(
            packageName = "com.android.systemui",
            appLabel = "系统界面",
            channels = listOf(
                Channel("com.android.systemui", "alerts", "提醒", ChannelImportance.DEFAULT),
            ),
            isSystem = true,
        ),
    )

    private val snapshot = ChannelSnapshotJson.Snapshot(
        apps = listOf(
            app(
                "com.example.chat",
                "聊天",
                ch("promo", "促销", ChannelImportance.NONE),
                ch("msg", "消息", ChannelImportance.HIGH),
                ch("gone", "已删除", ChannelImportance.LOW),
            ),
            app(
                "com.example.missing",
                "未安装",
                ch("any", "任意", ChannelImportance.NONE),
            ),
            app(
                "com.android.systemui",
                "系统界面",
                ch("alerts", "提醒", ChannelImportance.NONE),
            ),
        ),
    )

    @Test
    fun `plans apply skips same and system by default`() {
        val plan = ChannelSnapshotImportPlanner.plan(snapshot, local, includeSystem = false)
        assertEquals(3, plan.fileApps)
        assertEquals(5, plan.fileChannels)
        assertEquals(1, plan.applyCount)
        assertEquals("promo", plan.apply.single().channelId)
        assertEquals(ChannelImportance.NONE, plan.apply.single().targetImportance)
        assertEquals(1, plan.sameCount)
        assertEquals(1, plan.appMissingCount)
        assertEquals(1, plan.channelMissingCount)
        assertEquals(1, plan.systemHeldCount)
        assertTrue(plan.skips.any { it.reason.contains("未安装") })
        assertTrue(plan.skips.any { it.reason.contains("无此渠道") })
        assertTrue(plan.skips.any { it.reason.contains("系统应用") })
    }

    @Test
    fun `include system adds system apply targets`() {
        val plan = ChannelSnapshotImportPlanner.plan(snapshot, local, includeSystem = true)
        assertEquals(2, plan.applyCount)
        assertEquals(0, plan.systemHeldCount)
        assertTrue(plan.apply.any { it.packageName == "com.android.systemui" })
    }

    @Test
    fun `empty local inventory skips everything as missing app`() {
        val plan = ChannelSnapshotImportPlanner.plan(snapshot, emptyList())
        assertEquals(0, plan.applyCount)
        assertEquals(3, plan.appMissingCount)
        assertEquals(5, plan.fileChannels)
    }

    private fun app(pkg: String, label: String, vararg channels: Channel) =
        AppChannels(packageName = pkg, appLabel = label, channels = channels.toList())

    private fun ch(id: String, name: String, importance: ChannelImportance) =
        Channel(packageName = "", id = id, name = name, importance = importance)
}
