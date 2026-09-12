package com.github.iamr8.libman.ui

import com.github.iamr8.libman.cli.OpResultParser
import com.github.iamr8.libman.cli.UninstallOutcome
import com.github.iamr8.libman.cli.UpdateOutcome
import com.github.iamr8.libman.cli.WhatIfParser
import com.github.iamr8.libman.cli.WhatIfResult
import com.intellij.openapi.project.Project

/**
 * The user-facing LibMan operations, shared by the editor intentions and the context-menu actions.
 * Each runs off the EDT via [LibmanRun] and reports through [LibmanNotifications].
 */
object LibmanOps {

    /** Check for a newer version (read-only), then offer to apply it. */
    fun checkForUpdates(project: Project, manifestDir: String, name: String, pre: Boolean = false) {
        val label = if (pre) "$name (prerelease)" else name
        LibmanRun.run(
            project,
            title = "Checking $label for updates",
            op = "check",
            manifestDir = manifestDir,
            action = { it.whatIf(manifestDir, name, pre) },
            onOk = { r ->
                when (val res = WhatIfParser.parse(r.stdout)) {
                    is WhatIfResult.UpToDate ->
                        LibmanNotifications.info(project, name, "Already up to date.")

                    is WhatIfResult.WouldUpdate ->
                        LibmanNotifications.infoWithAction(
                            project,
                            "$name: update available",
                            "Latest version: ${res.version}",
                            actionText = "Update",
                        ) { update(project, manifestDir, name, pre) }

                    WhatIfResult.Unknown ->
                        LibmanNotifications.failure(
                            project, name, "Couldn't read the latest version.", r.combinedOutput(),
                        )
                }
            },
        )
    }

    /** Update a library to its latest (stable, or prerelease when [pre]). */
    fun update(project: Project, manifestDir: String, name: String, pre: Boolean = false) {
        LibmanRun.run(
            project,
            title = "Updating $name",
            op = "update",
            manifestDir = manifestDir,
            action = { it.update(manifestDir, name, pre) },
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
