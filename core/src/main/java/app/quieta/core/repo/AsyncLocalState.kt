package app.quieta.core.repo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Shared local state: asynchronous hydration, then serialized read-modify-write operations. */
internal class AsyncLocalState<T>(
    initialValue: T,
    private val load: () -> T,
    private val write: (T) -> Unit,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val mutableState = MutableStateFlow(initialValue)
    val state: StateFlow<T> = mutableState.asStateFlow()
    private val mutex = Mutex()
    private val initialized = scope.async(Dispatchers.IO) {
        mutableState.value = load()
    }

    suspend fun current(): T {
        initialized.await()
        return state.value
    }

    suspend fun update(transform: (T) -> T) = withContext(Dispatchers.IO) {
        initialized.await()
        mutex.withLock {
            val previous = mutableState.value
            val next = transform(previous)
            mutableState.value = next
            try {
                write(next)
            } catch (error: Exception) {
                mutableState.value = previous
                throw error
            }
        }
    }

    /** Re-read from disk (listener may have written from another process). */
    suspend fun reload() = withContext(Dispatchers.IO) {
        initialized.await()
        mutex.withLock {
            mutableState.value = load()
        }
    }
}
