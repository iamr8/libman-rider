package com.github.iamr8.libman.util

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Runs blocking work (e.g. network calls) with a fixed upper bound on parallelism. Pure (JDK only). */
object BoundedParallel {

    private const val POLL_MS = 50L

    /**
     * Runs [action] once per item, on at most [parallelism] threads of [executor] at a time, and
     * waits until all are done. While it waits, the calling thread runs [checkCanceled] every
     * [POLL_MS] ms. When that throws, no new item starts and the exception propagates right away
     * (running items finish on their own). The first exception from [action] stops new items and
     * is rethrown once the running ones end.
     */
    fun <T> forEach(
        items: List<T>,
        parallelism: Int,
        executor: Executor,
        checkCanceled: () -> Unit,
        action: (T) -> Unit,
    ) {
        if (items.isEmpty()) return
        val next = AtomicInteger()
        val stop = AtomicBoolean()
        val failure = AtomicReference<Throwable>()
        val workers = parallelism.coerceIn(1, items.size)
        val done = CountDownLatch(workers)
        repeat(workers) {
            executor.execute {
                try {
                    while (!stop.get()) {
                        val i = next.getAndIncrement()
                        if (i >= items.size) break
                        action(items[i])
                    }
                } catch (t: Throwable) {
                    failure.compareAndSet(null, t)
                    stop.set(true)
                } finally {
                    done.countDown()
                }
            }
        }
        try {
            while (!done.await(POLL_MS, TimeUnit.MILLISECONDS)) checkCanceled()
        } catch (t: Throwable) {
            stop.set(true)
            throw t
        }
        failure.get()?.let { throw it }
    }
}
