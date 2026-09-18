package app.quieta.core.engine

import app.quieta.core.model.ChannelImportance
import app.quieta.core.model.PrivilegeId
import app.quieta.core.privilege.ChannelSettingsPatch
import app.quieta.core.privilege.PrivilegeBackend
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import app.quieta.core.model.Channel as ModelChannel

class ApplyChannelSnapshotUseCaseTest {

    private class FakeBackend : PrivilegeBackend {
        override val id = PrivilegeId.SHIZUKU
        val patches = mutableListOf<ChannelSettingsPatch>()
        private val store = mutableMapOf(
            "com.a" to mutableListOf(
                ModelChannel("com.a", "promo", "促销", ChannelImportance.DEFAULT, soundEnabled = true),
                ModelChannel("com.a", "msg", "消息", ChannelImportance.HIGH, soundEnabled = true),
            ),
        )

        override suspend fun isAvailable(): Boolean = true

        override suspend fun listChannels(packageName: String): List<ModelChannel> =
            store[packageName].orEmpty().toList()

        override suspend fun setImportance(packageName: String, channelId: String, importance: Int) {
            applyChannelSettings(ChannelSettingsPatch(packageName, channelId, importance = importance))
        }

        override suspend fun applyChannelSettings(patch: ChannelSettingsPatch) {
            if (patch.channelId == "fail") error("write denied")
            if (patch.soundEnabled == false && patch.channelId == "sound-fail") {
                // Simulate ROM that accepts importance but ignores extras.
                val list = store[patch.packageName].orEmpty()
                store[patch.packageName] = list.map {
                    if (it.id == patch.channelId) it.copy(
                        importance = patch.importance?.let(::intToEnum) ?: it.importance,
                    ) else it
                }.toMutableList()
                patches += patch
                return
            }
            patches += patch
            val list = store[patch.packageName] ?: error("missing pkg")
            val idx = list.indexOfFirst { it.id == patch.channelId }
            if (idx < 0) error("missing channel")
            val cur = list[idx]
            list[idx] = cur.copy(
                importance = patch.importance?.let(::intToEnum) ?: cur.importance,
                soundEnabled = patch.soundEnabled ?: cur.soundEnabled,
                vibrationEnabled = patch.vibrationEnabled ?: cur.vibrationEnabled,
                lockscreenHidden = patch.lockscreenHidden ?: cur.lockscreenHidden,
            )
        }

        private fun intToEnum(v: Int): ChannelImportance = when (v) {
            0 -> ChannelImportance.NONE
            1 -> ChannelImportance.MIN
            2 -> ChannelImportance.LOW
            4, 5 -> ChannelImportance.HIGH
            else -> ChannelImportance.DEFAULT
        }
    }

    @Test
    fun `writes full settings and verifies via readback`() = runBlocking {
        val backend = FakeBackend()
        val useCase = ApplyChannelSnapshotUseCase(backend)
        val report = useCase.apply(
            listOf(
                SnapshotImportItem(
                    packageName = "com.a",
                    appLabel = "A",
                    channelId = "promo",
                    channelName = "促销",
                    currentImportance = ChannelImportance.DEFAULT,
                    targetImportance = ChannelImportance.NONE,
                    targetSoundEnabled = false,
                    targetVibrationEnabled = true,
                    targetLockscreenHidden = true,
                    importanceDiffers = true,
                    extrasDiffer = true,
                    isSystem = false,
                ),
                SnapshotImportItem(
                    packageName = "com.a",
                    appLabel = "A",
                    channelId = "fail",
                    channelName = "失败",
                    currentImportance = ChannelImportance.HIGH,
                    targetImportance = ChannelImportance.LOW,
                    targetSoundEnabled = true,
                    targetVibrationEnabled = false,
                    targetLockscreenHidden = false,
                    importanceDiffers = true,
                    extrasDiffer = false,
                    isSystem = false,
                ),
            ),
        )
        assertEquals(2, report.total)
        assertEquals(1, report.writeOk)
        assertEquals(1, report.writeFailed)
        assertEquals(1, report.verified)
        assertEquals(0, report.extrasOnly)
        assertTrue(report.errors.isNotEmpty())
        val patch = backend.patches.first { it.channelId == "promo" }
        assertEquals(0, patch.importance)
        assertEquals(false, patch.soundEnabled)
        assertEquals(true, patch.vibrationEnabled)
        assertEquals(true, patch.lockscreenHidden)
        val live = backend.listChannels("com.a").first { it.id == "promo" }
        assertEquals(ChannelImportance.NONE, live.importance)
        assertEquals(false, live.soundEnabled)
        assertEquals(true, live.vibrationEnabled)
        assertEquals(true, live.lockscreenHidden)
    }

    @Test
    fun `empty targets produce empty report`() = runBlocking {
        val report = ApplyChannelSnapshotUseCase(FakeBackend()).apply(emptyList())
        assertEquals(0, report.total)
        assertEquals(0, report.writeOk)
    }
}
