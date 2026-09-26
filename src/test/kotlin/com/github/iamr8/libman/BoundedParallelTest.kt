package com.github.iamr8.libman

import com.github.iamr8.libman.util.BoundedParallel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BoundedParallelTest {

    private val pool: ExecutorService = Executors.newCachedThreadPool()

    @After fun shutdown() {
        pool.shutdownNow()
    }

    @Test fun `runs every item once`() {
        val seen = Collections.synchronizedList(mutableListOf<Int>())
        BoundedParallel.forEach((1..20).toList(), 4, pool, {}) { seen += it }
        assertEquals((1..20).toList(), seen.sorted())
    }

    @Test fun `runs up to the bound in parallel, never more`() {
        val running = AtomicInteger()
        val maxRunning = AtomicInteger()
        // Each round only passes when exactly 3 items run at the same time; fewer would time out.
        val barrier = CyclicBarrier(3)
        BoundedParallel.forEach((1..9).toList(), 3, pool, {}) {
            maxRunning.accumulateAndGet(running.incrementAndGet()) { a, b -> maxOf(a, b) }
            barrier.await(10, TimeUnit.SECONDS)
            running.decrementAndGet()
        }
        assertEquals(3, maxRunning.get())
    }

    @Test fun `cancel stops new items and propagates`() {
        val single = Executors.newSingleThreadExecutor()
        val started = Collections.synchronizedList(mutableListOf<Int>())
        val firstStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        // Cancel only once item 1 runs, so the test does not depend on thread start timing.
        val cancel = { firstStarted.await(10, TimeUnit.SECONDS); throw CancellationException("closed") }
        try {
            BoundedParallel.forEach((1..10).toList(), 1, single, cancel) {
                started += it
                firstStarted.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
            fail("expected the cancel to propagate")
        } catch (e: CancellationException) {
            assertEquals("closed", e.message)
        }
        release.countDown()
        single.shutdown()
        assertTrue(single.awaitTermination(10, TimeUnit.SECONDS))
        assertEquals(listOf(1), started)
    }

    @Test(expected = IllegalStateException::class)
    fun `action failure is rethrown`() {
        BoundedParallel.forEach((1..5).toList(), 2, pool, {}) { if (it == 3) throw IllegalStateException("boom") }
    }

    @Test fun `empty list runs nothing`() {
        BoundedParallel.forEach(emptyList<Int>(), 4, { fail("no worker expected") }, {}) { fail("no item expected") }
    }
}
