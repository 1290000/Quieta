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

    /**
     * Write channel settings for snapshot import. Null fields are left unchanged.
     * Default only handles importance; backends that can mutate sound/vibration/lockscreen
     * must override this method.
     */
    suspend fun applyChannelSettings(patch: ChannelSettingsPatch) {
        patch.importance?.let { importance ->
            setImportance(patch.packageName, patch.channelId, importance)
        }
        if (patch.hasExtras) {
            error("Backend $id does not support sound/vibration/lockscreen write")
        }
    }
}
