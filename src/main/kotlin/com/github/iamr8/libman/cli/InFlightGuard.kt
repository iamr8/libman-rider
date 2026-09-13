package com.github.iamr8.libman.cli

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks in-flight operation keys so the same operation can't run twice concurrently.
 * [tryAcquire] returns false while a key is already held; call [release] when the operation ends.
 * Thread-safe.
 */
class InFlightGuard {

    private val active = ConcurrentHashMap.newKeySet<String>()

    /** Reserve [key]; returns true if acquired, false if an operation for it is already running. */
    fun tryAcquire(key: String): Boolean = active.add(key)

    /** Release [key] so the operation can run again. */
    fun release(key: String) { active.remove(key) }

    fun isActive(key: String): Boolean = active.contains(key)
}
