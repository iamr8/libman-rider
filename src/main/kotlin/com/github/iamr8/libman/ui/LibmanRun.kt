package com.github.iamr8.libman.ui

import com.github.iamr8.libman.cli.CliFailures
import com.github.iamr8.libman.cli.LibmanLocator
import com.github.iamr8.libman.cli.LibmanResult
import com.github.iamr8.libman.cli.LibmanRunner
import com.github.iamr8.libman.settings.LibmanSettings
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil

/** Runs `libman` off the EDT and routes the outcome to notifications / the error log. */
object LibmanRun {

    private val LOG = Logger.getInstance(LibmanRun::class.java)

    /**
     * Runs [action] against a [LibmanRunner] on a background thread, then handles the result on the
     * EDT: refreshes the manifest folder in the VFS and, on success, invokes [onOk]; on a CLI
     * failure shows a notification. A missing CLI is reported, not treated as an error. Unexpected
     * exceptions go to the IDE error reporter via `LOG.error`.
     *
     * @param op short verb for messages, e.g. "restore", "update".
     * @param title the progress/notification title (usually including the library name).
     */
    fun run(
        project: Project,
        title: String,
        op: String,
        manifestDir: String,
        action: (LibmanRunner) -> LibmanResult,
        onOk: (LibmanResult) -> Unit,
    ) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true) {
            private var notInstalled = false
            private var result: LibmanResult? = null

            override fun run(indicator: ProgressIndicator) {
                val settings = LibmanSettings.getInstance()
                // Stream each libman output line into the progress indicator (live step text).
                val runner = LibmanRunner(
                    libmanPath = LibmanLocator.resolve(settings.customLibmanPath),
                    verbosity = settings.verbosityArg,
                    onLine = { line -> indicator.text2 = line },
                )
                if (!runner.isInstalled()) {
                    notInstalled = true
                    return
                }
                result = action(runner)
            }

            override fun onSuccess() {
                if (notInstalled) {
                    LibmanNotifications.notInstalled(project)
                    return
                }
                val r = result ?: return
                refresh(manifestDir)
                if (r.ok) {
                    onOk(r)
                } else {
                    LibmanNotifications.failure(
                        project,
                        title,
                        CliFailures.describe(op, r.exitCode, r.stdout, r.stderr, r.timedOut),
                        r.combinedOutput(),
                    )
                }
            }

            override fun onThrowable(error: Throwable) {
                LOG.error("LibMan $op failed unexpectedly", error)
            }
        })
    }

    /** Refreshes the manifest folder so restored/removed files and the rewritten manifest show up. */
    private fun refresh(manifestDir: String) {
        val dir = LocalFileSystem.getInstance().refreshAndFindFileByPath(manifestDir) ?: return
        VfsUtil.markDirtyAndRefresh(true, true, true, dir)
    }
}
