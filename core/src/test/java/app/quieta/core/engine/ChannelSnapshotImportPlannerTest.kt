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
                Channel("com.example.chat", "promo", "促销", ChannelImportance.DEFAULT, soundEnabled = true),
                Channel("com.example.chat", "msg", "消息", ChannelImportance.HIGH, soundEnabled = true),
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

    private fun ch(
        id: String,
        name: String,
        importance: ChannelImportance,
        sound: Boolean = true,
        vibration: Boolean = false,
        lockHidden: Boolean = false,
    ) = Channel(
        packageName = "",
        id = id,
        name = name,
        importance = importance,
        soundEnabled = sound,
        vibrationEnabled = vibration,
        lockscreenHidden = lockHidden,
    )

    private val snapshot = ChannelSnapshotJson.Snapshot(
        apps = listOf(
            AppChannels(
                packageName = "com.example.chat",
                appLabel = "聊天",
                channels = listOf(
                    ch("promo", "促销", ChannelImportance.NONE),
                    ch("msg", "消息", ChannelImportance.HIGH),
                    ch("gone", "已删除", ChannelImportance.LOW),
                ),
            ),
            AppChannels(
                packageName = "com.example.missing",
                appLabel = "未安装",
                channels = listOf(ch("any", "任意", ChannelImportance.NONE)),
            ),
            AppChannels(
                packageName = "com.android.systemui",
                appLabel = "系统界面",
                channels = listOf(ch("alerts", "提醒", ChannelImportance.NONE)),
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
    }

    @Test
    fun `include system adds system apply targets`() {
        val plan = ChannelSnapshotImportPlanner.plan(snapshot, local, includeSystem = true)
        assertEquals(2, plan.applyCount)
        assertEquals(0, plan.systemHeldCount)
        assertTrue(plan.apply.any { it.packageName == "com.android.systemui" })
    }

    @Test
    fun `extras-only diff is planned`() {
        val snap = ChannelSnapshotJson.Snapshot(
            apps = listOf(
                AppChannels(
                    packageName = "com.example.chat",
                    appLabel = "聊天",
                    channels = listOf(
                        // Same importance as local promo (DEFAULT), but sound off + vibration on.
                        ch("promo", "促销", ChannelImportance.DEFAULT, sound = false, vibration = true),
                    ),
                ),
            ),
        )
        val plan = ChannelSnapshotImportPlanner.plan(snap, local)
        assertEquals(1, plan.applyCount)
        val item = plan.apply.single()
        assertEquals(false, item.importanceDiffers)
        assertEquals(true, item.extrasDiffer)
        assertEquals(false, item.targetSoundEnabled)
        assertEquals(true, item.targetVibrationEnabled)
        assertTrue(item.extrasSummary().contains("声音关"))
        assertEquals(1, plan.extrasApplyCount)
    }

    @Test
    fun `empty local inventory skips everything as missing app`() {
        val plan = ChannelSnapshotImportPlanner.plan(snapshot, emptyList())
        assertEquals(0, plan.applyCount)
        assertEquals(3, plan.appMissingCount)
        assertEquals(5, plan.fileChannels)
    }
}
