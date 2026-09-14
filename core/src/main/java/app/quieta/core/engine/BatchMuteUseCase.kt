package app.quieta.core.engine

import app.quieta.core.model.Channel
import app.quieta.core.model.RuleAction
import app.quieta.core.privilege.PrivilegeBackend
import kotlinx.coroutines.delay

data class MuteResult(
    val total: Int,
    val success: Int,
    val failed: Int,
    val errors: List<String> = emptyList(),
)

/**
 * Applies MUTE/DOWNGRADE plans with throttling so Binder is not flooded.
 */
class BatchMuteUseCase(
    private val backend: PrivilegeBackend,
    private val batchSize: Int = 20,
    private val batchDelayMs: Long = 50L,
) {
    suspend fun apply(plan: Map<Channel, RuleAction>): MuteResult {
        val targets = plan.filterValues { it == RuleAction.MUTE || it == RuleAction.DOWNGRADE }
        if (targets.isEmpty()) return MuteResult(0, 0, 0)

        var success = 0
        var failed = 0
        val errors = mutableListOf<String>()
        val entries = targets.entries.toList()

        entries.chunked(batchSize).forEach { batch ->
            batch.forEach { (channel, action) ->
                runCatching {
                    val importance = when (action) {
                        RuleAction.MUTE -> 0
                        RuleAction.DOWNGRADE -> 2
                        else -> return@runCatching
                    }
                    backend.setImportance(channel.packageName, channel.id, importance)
                    success++
                }.onFailure { e ->
                    failed++
                    if (errors.size < 8) {
                        errors += "${channel.packageName}/${channel.id}: ${e.message}"
                    }
                }
            }
            delay(batchDelayMs)
        }
        return MuteResult(total = targets.size, success = success, failed = failed, errors = errors)
    }
}
