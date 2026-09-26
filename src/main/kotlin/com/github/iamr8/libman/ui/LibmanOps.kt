package com.github.iamr8.libman.ui

import com.github.iamr8.libman.cli.PendingSummary
import com.github.iamr8.libman.model.PendingChange
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil

/**
 * The user-facing LibMan operations, shared by the inline action links and the context-menu actions.
 * Each runs off the EDT via [LibmanRun] and reports through [LibmanNotifications].
 */
object LibmanOps {

    /**
     * Runs the queued [changes] of one manifest, in order, as one background task, then shows one
     * summary. [onDone] gets the changes that did not run (the task stopped early), so they can go
     * back in the queue.
     *
     * @return false, and nothing runs, when another operation on this manifest is running.
     */
    fun applyPending(
        project: Project,
        manifestDir: String,
        changes: List<PendingChange>,
        onDone: (notRun: List<PendingChange>) -> Unit,
    ): Boolean = LibmanRun.runSequence(
        project,
        title = "Applying LibMan changes",
        manifestDir = manifestDir,
        // The manifest key: never overlaps restore/clean, or another apply, on the same manifest.
        key = manifestKey(manifestDir),
        steps = changes,
        stepTitle = { c ->
            when (c) {
                is PendingChange.Update -> "Installing ${c.name}@${c.to}"
                is PendingChange.Remove -> "Uninstalling ${c.name}"
            }
        },
        action = { runner, c ->
            when (c) {
                is PendingChange.Update -> runner.update(manifestDir, c.name, to = c.to)
                is PendingChange.Remove -> runner.uninstall(manifestDir, c.name)
            }
        },
        onDone = { results ->
            notifySummary(project, PendingSummary.of(results))
            onDone(changes.drop(results.size))
        },
    )

    private fun notifySummary(project: Project, summary: PendingSummary) {
        fun html(lines: List<String>) = lines.joinToString("<br/>") { StringUtil.escapeXmlEntities(it) }
        if (summary.applied.isNotEmpty()) LibmanNotifications.info(project, "LibMan", html(summary.applied))
        if (summary.failed.isNotEmpty()) LibmanNotifications.failure(project, "LibMan", html(summary.failed), summary.details)
    }

    /** Restore every library defined in the manifest. */
    fun restore(project: Project, manifestDir: String) {
        LibmanRun.run(
            project,
            title = "Restoring client-side libraries",
            op = "restore",
            manifestDir = manifestDir,
            key = manifestKey(manifestDir),
            action = { it.restore(manifestDir) },
            onOk = { LibmanNotifications.info(project, "LibMan", "Client-side libraries restored.") },
        )
    }

    /** Clean files previously restored via LibMan (manifest entries stay). */
    fun clean(project: Project, manifestDir: String) {
        LibmanRun.run(
            project,
            title = "Cleaning client-side libraries",
            op = "clean",
            manifestDir = manifestDir,
            key = manifestKey(manifestDir),
            action = { it.clean(manifestDir) },
            onOk = { LibmanNotifications.info(project, "LibMan", "Client-side libraries cleaned.") },
        )
    }

    // Whole-manifest ops (restore/clean/apply) share one key, so they never overlap on the same
    // manifest. NUL can't appear in a path, so the key never collides with a path.
    private fun manifestKey(manifestDir: String): String = "$manifestDir\u0000"
}
