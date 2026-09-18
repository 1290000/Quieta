// SPDX-License-Identifier: GPL-3.0-only
package app.quieta.core.settings

import android.content.Context
import java.io.File
import org.json.JSONObject

/**
 * Cross-process flags/state for the notification listener.
 *
 * NLS may run in a separate process; DataStore and process-local statics are
 * not reliable across processes. A tiny JSON file under filesDir is enough.
 */
object ListenerFlagStore {

    data class Flags(
        val timelineEnabled: Boolean = true,
        val autoMuteEnabled: Boolean = false,
        val healthCheckEnabled: Boolean = false,
        val keepAliveEnabled: Boolean = false,
    )

    data class NlsState(
        val connected: Boolean = false,
        val connectedAt: Long = 0L,
        val lastEventAt: Long = 0L,
        val lastCycleAt: Long = 0L,
    )

    private fun flagsFile(context: Context) =
        File(context.applicationContext.filesDir, "listener_flags.json")

    private fun stateFile(context: Context) =
        File(context.applicationContext.filesDir, "nls_state.json")

    fun readFlags(context: Context): Flags {
        val file = flagsFile(context)
        if (!file.exists()) return Flags()
        return runCatching {
            val o = JSONObject(file.readText())
            Flags(
                timelineEnabled = o.optBoolean("timelineEnabled", true),
                autoMuteEnabled = o.optBoolean("autoMuteEnabled", false),
                healthCheckEnabled = o.optBoolean("healthCheckEnabled", false),
                keepAliveEnabled = o.optBoolean("keepAliveEnabled", false),
            )
        }.getOrDefault(Flags())
    }

    fun writeFlags(context: Context, flags: Flags) {
        val file = flagsFile(context)
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(
                JSONObject()
                    .put("timelineEnabled", flags.timelineEnabled)
                    .put("autoMuteEnabled", flags.autoMuteEnabled)
                    .put("healthCheckEnabled", flags.healthCheckEnabled)
                    .put("keepAliveEnabled", flags.keepAliveEnabled)
                    .toString(),
            )
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    fun readState(context: Context): NlsState {
        val file = stateFile(context)
        if (!file.exists()) return NlsState()
        return runCatching {
            val o = JSONObject(file.readText())
            NlsState(
                connected = o.optBoolean("connected", false),
                connectedAt = o.optLong("connectedAt", 0L),
                lastEventAt = o.optLong("lastEventAt", 0L),
                lastCycleAt = o.optLong("lastCycleAt", 0L),
            )
        }.getOrDefault(NlsState())
    }

    fun writeState(context: Context, state: NlsState) {
        val file = stateFile(context)
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(
                JSONObject()
                    .put("connected", state.connected)
                    .put("connectedAt", state.connectedAt)
                    .put("lastEventAt", state.lastEventAt)
                    .put("lastCycleAt", state.lastCycleAt)
                    .toString(),
            )
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    fun markConnected(context: Context, connected: Boolean) {
        val current = readState(context)
        writeState(
            context,
            current.copy(
                connected = connected,
                connectedAt = if (connected) System.currentTimeMillis() else current.connectedAt,
            ),
        )
    }

    fun markEvent(context: Context) {
        val current = readState(context)
        writeState(context, current.copy(lastEventAt = System.currentTimeMillis()))
    }

    fun markCycle(context: Context) {
        val current = readState(context)
        writeState(context, current.copy(lastCycleAt = System.currentTimeMillis()))
    }
}
