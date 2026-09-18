package app.quieta.core.engine

import app.quieta.core.privilege.ChannelSettingsPatch
import app.quieta.core.privilege.PrivilegeBackend
import kotlinx.coroutines.delay

data class SnapshotApplyReport(
    val total: Int = 0,
    val writeOk: Int = 0,
    val writeFailed: Int = 0,
    val verified: Int = 0,
    val unconfirmed: Int = 0,
    val mismatch: Int = 0,
    val extrasOnly: Int = 0,
    val errors: List<String> = emptyList(),
    val verifyNotes: List<String> = emptyList(),
) {
    val done: Boolean get() = true
}

/**
 * Writes snapshot channel settings (importance + sound + vibration + lockscreen)
 * with Binder throttle, then reads back live channels to verify.
 */
class ApplyChannelSnapshotUseCase(
    private val backend: PrivilegeBackend,
    private val batchSize: Int = 20,
    private val batchDelayMs: Long = 40L,
) {
    suspend fun apply(
        items: List<SnapshotImportItem>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): SnapshotApplyReport {
        if (items.isEmpty()) {
            return SnapshotApplyReport()
        }
        var writeOk = 0
        var writeFailed = 0
        val errors = mutableListOf<String>()
        val written = mutableListOf<SnapshotImportItem>()
        val total = items.size
        var done = 0

        items.chunked(batchSize).forEach { batch ->
            batch.forEach { item ->
                runCatching {
                    backend.applyChannelSettings(
                        ChannelSettingsPatch(
                            packageName = item.packageName,
                            channelId = item.channelId,
                            importance = MuteUndoStore.importanceToInt(item.targetImportance),
                            soundEnabled = item.targetSoundEnabled,
                            vibrationEnabled = item.targetVibrationEnabled,
                            lockscreenHidden = item.targetLockscreenHidden,
                        ),
                    )
                    writeOk++
                    written += item
                }.onFailure { e ->
                    writeFailed++
                    if (errors.size < 8) {
                        errors += "${item.packageName}/${item.channelId}: ${e.message}"
                    }
                }
                done++
            }
            onProgress(done, total)
            if (items.size > batchSize) delay(batchDelayMs)
        }

        var verified = 0
        var unconfirmed = 0
        var mismatch = 0
        var extrasOnly = 0
        val verifyNotes = mutableListOf<String>()
        written.groupBy { it.packageName }.forEach { (pkg, chans) ->
            runCatching {
                val live = backend.listChannels(pkg).associateBy { it.id }
                chans.forEach { item ->
                    val channel = live[item.channelId]
                    if (channel == null) {
                        unconfirmed++
                        if (verifyNotes.size < 8) {
                            verifyNotes += "$pkg/${item.channelId}: 回读无此渠道"
                        }
                        return@forEach
                    }
                    val impOk = channel.importance == item.targetImportance
                    val soundOk = channel.soundEnabled == item.targetSoundEnabled
                    val vibOk = channel.vibrationEnabled == item.targetVibrationEnabled
                    val lockOk = channel.lockscreenHidden == item.targetLockscreenHidden
                    when {
                        impOk && soundOk && vibOk && lockOk -> verified++
                        impOk && !(soundOk && vibOk && lockOk) -> {
                            // Importance took; extras may be ignored by ROM or importance NONE suppresses vibration.
                            extrasOnly++
                            if (verifyNotes.size < 8) {
                                val bits = buildList {
                                    if (!soundOk) add("声音")
                                    if (!vibOk) add("震动")
                                    if (!lockOk) add("锁屏")
                                }
                                verifyNotes += "$pkg/${item.channelId}: importance 已写入，${bits.joinToString("/")} 未完全生效"
                            }
                        }
                        else -> {
                            mismatch++
                            if (verifyNotes.size < 8) {
                                verifyNotes += "$pkg/${item.channelId}: 期望 ${item.targetImportance} 实际 ${channel.importance}"
                            }
                        }
                    }
                }
            }.onFailure { e ->
                unconfirmed += chans.size
                if (verifyNotes.size < 8) {
                    verifyNotes += "$pkg: 回读失败 ${e.message}"
                }
            }
        }

        return SnapshotApplyReport(
            total = total,
            writeOk = writeOk,
            writeFailed = writeFailed,
            verified = verified,
            unconfirmed = unconfirmed,
            mismatch = mismatch,
            extrasOnly = extrasOnly,
            errors = errors,
            verifyNotes = verifyNotes,
        )
    }
}
