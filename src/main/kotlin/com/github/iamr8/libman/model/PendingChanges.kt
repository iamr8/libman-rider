package com.github.iamr8.libman.model

/** A queued library change. It runs on the next explicit save, on file close, or on "Apply now". */
sealed interface PendingChange {
    val name: String

    data class Update(override val name: String, val to: String) : PendingChange
    data class Remove(override val name: String) : PendingChange
}

/**
 * Pure operations on one manifest's queue: an immutable list with at most one change per library.
 * Platform-free for unit testing.
 */
object PendingChanges {

    /** Adds [change]. An earlier change for the same library is replaced in its place (the last click wins). */
    fun add(queue: List<PendingChange>, change: PendingChange): List<PendingChange> {
        val i = queue.indexOfFirst { it.name == change.name }
        return if (i < 0) queue + change else queue.toMutableList().also { it[i] = change }
    }

    /** Drops the change for library [name], if any. */
    fun cancel(queue: List<PendingChange>, name: String): List<PendingChange> =
        queue.filterNot { it.name == name }

    /** The change for library [name], or null. */
    fun find(queue: List<PendingChange>, name: String): PendingChange? =
        queue.firstOrNull { it.name == name }

    /**
     * Puts changes that did not run back in front of [queue], in their order. A change queued
     * since then for the same library is newer, so it wins.
     */
    fun requeue(queue: List<PendingChange>, notRun: List<PendingChange>): List<PendingChange> =
        notRun.filterNot { n -> queue.any { it.name == n.name } } + queue

    /** Short label for the action row, e.g. "update to 3.7.1" or "remove". */
    fun label(change: PendingChange): String = when (change) {
        is PendingChange.Update -> "update to ${change.to}"
        is PendingChange.Remove -> "remove"
    }
}
