package app.quieta.core.engine

import app.quieta.core.model.Channel
import app.quieta.core.model.ChannelImportance
import app.quieta.core.model.PrivilegeId
import app.quieta.core.privilege.PrivilegeBackend
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyChannelSnapshotUseCaseTest {

    private class FakeBackend : PrivilegeBackend {
        override val id = PrivilegeId.SHIZUKU
        val writes = mutableListOf<Triple<String, String, Int>>()
        private val store = mutableMapOf(
            "com.a" to listOf(
                Channel("com.a", "promo", "促销", ChannelImportance.DEFAULT),
                Channel("com.a", "msg", "消息", ChannelImportance.HIGH),
            ),
        )

        override suspend fun isAvailable(): Boolean = true

        override suspend fun listChannels(packageName: String): List<Channel> =
            store[packageName].orEmpty()

        override suspend fun setImportance(packageName: String, channelId: String, importance: Int) {
            if (channelId == "fail") error("write denied")
            writes += Triple(packageName, channelId, importance)
            val list = store[packageName].orEmpty()
            store[packageName] = list.map {
                if (it.id == channelId) it.copy(importance = intToEnum(importance)) else it
            }
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
    fun `writes targets and verifies via readback`() = runBlocking {
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
                    isSystem = false,
                ),
                SnapshotImportItem(
                    packageName = "com.a",
                    appLabel = "A",
                    channelId = "fail",
                    channelName = "失败",
                    currentImportance = ChannelImportance.HIGH,
                    targetImportance = ChannelImportance.LOW,
                    isSystem = false,
                ),
            ),
        )
        assertEquals(2, report.total)
        assertEquals(1, report.writeOk)
        assertEquals(1, report.writeFailed)
        assertEquals(1, report.verified)
        assertTrue(report.errors.isNotEmpty())
        assertEquals(listOf(0), backend.writes.map { it.third })
    }

    @Test
    fun `empty targets produce empty report`() = runBlocking {
        val report = ApplyChannelSnapshotUseCase(FakeBackend()).apply(emptyList())
        assertEquals(0, report.total)
        assertEquals(0, report.writeOk)
    }
}
