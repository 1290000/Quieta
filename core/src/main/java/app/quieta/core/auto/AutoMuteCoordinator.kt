package app.quieta.core.auto

import android.util.Log
import app.quieta.core.engine.RulesEngine
import app.quieta.core.model.Channel
import app.quieta.core.model.RuleAction
import app.quieta.core.privilege.PrivilegeBackends
import app.quieta.core.repo.RuleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * When auto-mute is on, newly seen channels are matched against rules and muted/downgraded.
 * Designed to be cheap: only acts on channels not seen before.
 */
object AutoMuteCoordinator {

    private const val TAG = "AutoMute"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val seenChannelKeys = mutableSetOf<String>()

    fun onChannelSeen(
        context: android.content.Context,
        packageName: String,
        channelId: String,
        channelName: String,
        importance: Int,
        repository: RuleRepository,
        autoMuteEnabled: Boolean,
    ) {
        if (!autoMuteEnabled) return
        val key = "$packageName/$channelId"
        val firstSeen = synchronized(seenChannelKeys) { seenChannelKeys.add(key) }
        if (!firstSeen) return

        scope.launch {
            mutex.withLock {
                runCatching {
                    val rules = repository.rules.first()
                    if (rules.isEmpty()) return@runCatching
                    val channel = Channel(
                        packageName = packageName,
                        id = channelId,
                        name = channelName.ifEmpty { channelId },
                        importance = when {
                            importance <= 0 -> app.quieta.core.model.ChannelImportance.NONE
                            importance <= 2 -> app.quieta.core.model.ChannelImportance.LOW
                            importance >= 5 -> app.quieta.core.model.ChannelImportance.HIGH
                            else -> app.quieta.core.model.ChannelImportance.DEFAULT
                        },
                    )
                    val action = RulesEngine(rules).actionFor(channel)
                    if (action == RuleAction.MUTE || action == RuleAction.DOWNGRADE) {
                        val backend = PrivilegeBackends.preferred(context) ?: return@runCatching
                        val target = if (action == RuleAction.MUTE) 0 else 2
                        backend.setImportance(packageName, channelId, target)
                        Log.i(TAG, "auto $action $key -> $target")
                    }
                }.onFailure { e ->
                    Log.w(TAG, "auto mute failed for $key", e)
                }
            }
        }
    }

    /** Seed known channels so we do not re-mute everything on first enable. */
    fun seedKnown(channels: Collection<Channel>) {
        synchronized(seenChannelKeys) {
            channels.forEach { seenChannelKeys += "${it.packageName}/${it.id}" }
        }
    }
}
