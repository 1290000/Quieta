package app.quieta.core.repo

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AsyncLocalStateTest {
    @Test fun constructorDoesNotWaitForDiskAndCurrentWaitsForHydration() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val caller = Thread.currentThread()
        val store = AsyncLocalState(emptyList<Int>(), load = {
            assertNotSame(caller, Thread.currentThread())
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            listOf(42)
        }, write = {})
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(emptyList<Int>(), store.state.value)
        } finally {
            release.countDown()
        }
        assertEquals(listOf(42), store.current())
    }

    @Test fun updatesWaitForDiskAndDoNotLoseConcurrentChanges() = runBlocking {
        val release = CountDownLatch(1)
        val writes = AtomicInteger()
        val loads = AtomicInteger()
        val store = AsyncLocalState(0, load = {
            loads.incrementAndGet()
            check(release.await(5, TimeUnit.SECONDS))
            10
        }, write = { writes.incrementAndGet() })
        val jobs = List(50) { async(start = CoroutineStart.UNDISPATCHED) { store.update { it + 1 } } }
        assertTrue(jobs.none { it.isCompleted })
        release.countDown()
        jobs.awaitAll()
        assertEquals(60, store.current())
        assertEquals(50, writes.get())
        assertEquals(1, loads.get())
    }

    @Test fun sharedFlowPublishesBeforeWriteAndRollsBackOnFailure() = runBlocking {
        lateinit var store: AsyncLocalState<Int>
        store = AsyncLocalState(0, load = { 5 }, write = {
            assertEquals(it, store.state.value)
            error("Disk full")
        })
        try {
            store.update { it + 1 }
            fail("Write must fail")
        } catch (_: IllegalStateException) {
            assertEquals(5, store.current())
        }
    }
}
