package app.quieta.core.engine

import app.quieta.core.model.Channel
import app.quieta.core.model.RuleAction
import app.quieta.core.privilege.PrivilegeBackend
import kotlinx.coroutines.delay

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
    private val batchSize: Int = 20,
    private val batchDelayMs: Long = 40L,
) {
    suspend fun apply(channel: Channel, action: RuleAction): Result<Unit> {
        val importance = ChannelImportanceWriter.targetImportance(action)
            ?: return Result.failure(IllegalArgumentException("Unsupported action $action"))
        return runCatching {
            backend.setImportance(channel.packageName, channel.id, importance)
        }
    }

    /** Applies the same action to every channel of one app. */
    suspend fun applyToApp(channels: List<Channel>, action: RuleAction): MuteResult {
        val importance = ChannelImportanceWriter.targetImportance(action)
            ?: return MuteResult(0, 0, 0, listOf("Unsupported action $action"))
        if (channels.isEmpty()) return MuteResult(0, 0, 0)
        var success = 0
        var failed = 0
        val errors = mutableListOf<String>()
        channels.chunked(batchSize).forEach { batch ->
            batch.forEach { channel ->
                runCatching {
                    backend.setImportance(channel.packageName, channel.id, importance)
                    success++
                }.onFailure { e ->
                    failed++
                    if (errors.size < 8) {
                        errors += channel.packageName + "/" + channel.id + ": " + e.message
                    }
                }
            }
            if (channels.size > batchSize) delay(batchDelayMs)
        }
        return MuteResult(total = channels.size, success = success, failed = failed, errors = errors)
    }

    /** Restores each channel to the recorded pre-mute importance. */
    suspend fun restoreEntries(entries: List<MuteSnapshotEntry>): MuteResult {
        if (entries.isEmpty()) return MuteResult(0, 0, 0)
        var success = 0
        var failed = 0
        val errors = mutableListOf<String>()
        entries.chunked(batchSize).forEach { batch ->
            batch.forEach { entry ->
                runCatching {
                    backend.setImportance(entry.packageName, entry.channelId, entry.previousImportance)
                    success++
                }.onFailure { e ->
                    failed++
                    if (errors.size < 8) {
                        errors += entry.packageName + "/" + entry.channelId + ": " + e.message
                    }
                }
            }
            if (entries.size > batchSize) delay(batchDelayMs)
        }
        return MuteResult(total = entries.size, success = success, failed = failed, errors = errors)
    }
}
