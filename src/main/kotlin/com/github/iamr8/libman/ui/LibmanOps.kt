package com.github.iamr8.libman.ui

import com.github.iamr8.libman.cli.OpResultParser
import com.github.iamr8.libman.cli.UninstallOutcome
import com.github.iamr8.libman.cli.UpdateOutcome
import com.intellij.openapi.project.Project

/**
 * The user-facing LibMan operations, shared by the inline action links and the context-menu actions.
 * Each runs off the EDT via [LibmanRun] and reports through [LibmanNotifications].
 */
object LibmanOps {

    /**
     * Update a library. With [to] set, installs that exact version (used by the version chips);
     * otherwise moves to the latest (stable, or prerelease when [pre]).
     */
    fun update(project: Project, manifestDir: String, name: String, pre: Boolean = false, to: String? = null) {
        LibmanRun.run(
            project,
            title = if (to != null) "Installing $name@$to" else "Updating $name",
            op = "update",
            manifestDir = manifestDir,
            action = { it.update(manifestDir, name, pre, to) },
            // libman exits 0 even for a no-op or a missing library, so read the outcome from stdout.
            onOk = { r ->
                when (val outcome = OpResultParser.parseUpdate(r.stdout)) {
                    is UpdateOutcome.Updated -> LibmanNotifications.info(project, name, "Updated to ${outcome.version}.")
                    UpdateOutcome.AlreadyLatest -> LibmanNotifications.info(project, name, "Already up to date.")
                    UpdateOutcome.NotFound ->
                        LibmanNotifications.failure(project, name, "No library named \"$name\" in the manifest.", r.combinedOutput())
                    UpdateOutcome.Unknown ->
                        LibmanNotifications.failure(project, name, "Update result was unclear.", r.combinedOutput())
                }
            },
        )
    }

    /** Uninstall a library: remove its files and its manifest entry. */
    fun uninstall(project: Project, manifestDir: String, name: String) {
        LibmanRun.run(
            project,
            title = "Uninstalling $name",
            op = "uninstall",
            manifestDir = manifestDir,
            action = { it.uninstall(manifestDir, name) },
            onOk = { r ->
                when (OpResultParser.parseUninstall(r.stdout)) {
                    UninstallOutcome.Uninstalled -> LibmanNotifications.info(project, name, "Uninstalled.")
                    UninstallOutcome.NotInstalled ->
                        LibmanNotifications.failure(project, name, "\"$name\" is not installed.", r.combinedOutput())
                    UninstallOutcome.Unknown ->
                        LibmanNotifications.failure(project, name, "Uninstall result was unclear.", r.combinedOutput())
                }
            },
        )
    }

    /** Restore every library defined in the manifest. */
    fun restore(project: Project, manifestDir: String) {
        LibmanRun.run(
            project,
            title = "Restoring client-side libraries",
            op = "restore",
            manifestDir = manifestDir,
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
            action = { it.clean(manifestDir) },
            onOk = { LibmanNotifications.info(project, "LibMan", "Client-side libraries cleaned.") },
        )
    }
}
