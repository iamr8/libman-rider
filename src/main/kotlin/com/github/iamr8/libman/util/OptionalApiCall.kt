package com.github.iamr8.libman.util

/**
 * Runs a call into a platform API that may change or go away in a future IDE (an internal or impl
 * class). The plugin is built against one SDK, so a removed class or method fails only at runtime,
 * as a [LinkageError] (NoClassDefFoundError, NoSuchMethodError). On the first one, [onBroken] is
 * called and every later [run] skips the block. Other exceptions are not caught.
 *
 * Pure and platform-free for unit testing.
 */
class OptionalApiCall(private val onBroken: (LinkageError) -> Unit) {

    @Volatile
    private var broken = false

    /** Runs [block]. Returns `false` if the API is broken (now or on an earlier call). */
    fun run(block: () -> Unit): Boolean {
        if (broken) return false
        return try {
            block()
            true
        } catch (e: LinkageError) {
            broken = true
            onBroken(e)
            false
        }
    }
}
