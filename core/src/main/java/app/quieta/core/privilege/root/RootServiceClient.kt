package app.quieta.core.privilege.root

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

/** Serializes root IPC and releases both service and launch shell after a short idle window. */
internal class RootServiceClient private constructor(private val context: Context) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var connection: ServiceConnection? = null
    private var service: IRootChannels? = null
    private var idleJob: Job? = null

    suspend fun <T> use(block: (IRootChannels) -> T): T = mutex.withLock {
        idleJob?.cancel()
        try {
            val remote = connect()
            withContext(Dispatchers.IO) { block(remote) }
        } finally {
            idleJob = scope.launch {
                delay(1500)
                mutex.withLock { withContext(NonCancellable) { disconnect() } }
            }
        }
    }

    private suspend fun connect(): IRootChannels {
        service?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
        disconnect()
        val ready = CompletableDeferred<IRootChannels>()
        val callback = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (connection !== this) {
                    RootService.unbind(this)
                } else {
                    ready.complete(IRootChannels.Stub.asInterface(binder))
                }
            }
            override fun onServiceDisconnected(name: ComponentName) {
                ready.completeExceptionally(IllegalStateException("Root service disconnected"))
            }
            override fun onNullBinding(name: ComponentName) {
                ready.completeExceptionally(IllegalStateException("Root service returned no Binder"))
            }
            override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
        }
        connection = callback
        try {
            withContext(Dispatchers.Main.immediate) {
                RootService.bind(Intent(context, RootChannelService::class.java), callback)
            }
            return withTimeout(20_000) { ready.await() }.also { service = it }
        } catch (error: TimeoutCancellationException) {
            withContext(NonCancellable) { disconnect() }
            throw IllegalStateException("Root service connection timed out", error)
        } catch (error: Exception) {
            withContext(NonCancellable) { disconnect() }
            throw error
        }
    }

    private suspend fun disconnect() {
        val old = connection
        connection = null
        service = null
        if (old != null) withContext(Dispatchers.Main.immediate) { RootService.unbind(old) }
        withContext(Dispatchers.IO) { Shell.getCachedShell()?.close() }
    }

    companion object {
        @Volatile private var instance: RootServiceClient? = null
        fun getInstance(context: Context): RootServiceClient = instance ?: synchronized(this) {
            instance ?: RootServiceClient(context.applicationContext).also { instance = it }
        }
    }
}
