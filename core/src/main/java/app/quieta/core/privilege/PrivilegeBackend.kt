package app.quieta.core.privilege

import app.quieta.core.model.Channel
import app.quieta.core.model.PrivilegeId

/**
 * Elevated capability surface. UI must not bind Shizuku/Root directly.
 */
interface PrivilegeBackend {
    val id: PrivilegeId

    suspend fun isAvailable(): Boolean

    suspend fun listChannels(packageName: String): List<Channel>

    suspend fun setImportance(
        packageName: String,
        channelId: String,
        importance: Int,
    )
}
