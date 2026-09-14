package app.quieta.core.privilege

import app.quieta.core.model.Channel
import app.quieta.core.model.ChannelImportance
import app.quieta.core.model.PrivilegeId

/** In-memory backend for previews and unit tests. */
class FakePrivilegeBackend(
    private val available: Boolean = true,
) : PrivilegeBackend {

    override val id: PrivilegeId = PrivilegeId.NONE

    override suspend fun isAvailable(): Boolean = available

    override suspend fun listChannels(packageName: String): List<Channel> =
        if (!available) {
            emptyList()
        } else {
            listOf(
                Channel(
                    packageName = packageName,
                    id = "promo",
                    name = "推广",
                    importance = ChannelImportance.DEFAULT,
                ),
                Channel(
                    packageName = packageName,
                    id = "order",
                    name = "订单",
                    importance = ChannelImportance.HIGH,
                ),
            )
        }

    override suspend fun setImportance(
        packageName: String,
        channelId: String,
        importance: Int,
    ) = Unit
}
