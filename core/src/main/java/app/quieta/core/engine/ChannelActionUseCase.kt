package app.quieta.core.engine

import app.quieta.core.model.Channel
import app.quieta.core.model.RuleAction
import app.quieta.core.privilege.PrivilegeBackend

/** Single-channel importance write used by row actions and batch mute. */
object ChannelImportanceWriter {
    fun targetImportance(action: RuleAction): Int? = when (action) {
        RuleAction.MUTE -> 0
        RuleAction.DOWNGRADE -> 2
        RuleAction.KEEP -> 3 // restore to DEFAULT
    }
}

class ChannelActionUseCase(
    private val backend: PrivilegeBackend,
) {
    suspend fun apply(channel: Channel, action: RuleAction): Result<Unit> {
        val importance = ChannelImportanceWriter.targetImportance(action)
            ?: return Result.failure(IllegalArgumentException("Unsupported action $action"))
        return runCatching {
            backend.setImportance(channel.packageName, channel.id, importance)
        }
    }
}
