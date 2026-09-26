package com.github.iamr8.libman.ui

import com.github.iamr8.libman.cli.CliFailures
import com.github.iamr8.libman.cli.InFlightGuard
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
    // One operation per key at a time (key = manifest + library, or manifest + whole-manifest op).
    // A second click while an operation is running is ignored, so a double-click can't run twice.
    private val inFlight = InFlightGuard()

    fun run(
        project: Project,
        title: String,
        op: String,
        manifestDir: String,
        key: String,
        action: (LibmanRunner) -> LibmanResult,
        onOk: (LibmanResult) -> Unit,
    ) {
        if (!inFlight.tryAcquire(key)) return // an operation for this key is already running
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

            // Runs after onSuccess / onThrowable / cancel — always release the key here.
            override fun onFinished() {
                inFlight.release(key)
            }
        })
    }

    /**
     * Runs [steps] one after another in one background task, so two `libman` processes never
     * rewrite the same `libman.json` at once. The CLI install check runs once, before the first
     * step. [onDone] always runs on the EDT with the results so far; fewer results than steps means
     * the task stopped early (cancel, missing CLI, or an unexpected error).
     *
     * @return false, and nothing runs, when an operation for [key] is already running.
     */
    fun <T> runSequence(
        project: Project,
        title: String,
        manifestDir: String,
        key: String,
        steps: List<T>,
        stepTitle: (T) -> String,
        action: (LibmanRunner, T) -> LibmanResult,
        onDone: (List<Pair<T, LibmanResult>>) -> Unit,
    ): Boolean {
        if (!inFlight.tryAcquire(key)) return false
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true) {
            private var notInstalled = false
            private val results = mutableListOf<Pair<T, LibmanResult>>()

            override fun run(indicator: ProgressIndicator) {
                val settings = LibmanSettings.getInstance()
                val runner = LibmanRunner(
                    libmanPath = LibmanLocator.resolve(settings.customLibmanPath),
                    verbosity = settings.verbosityArg,
                    onLine = { line -> indicator.text2 = line },
                )
                if (!runner.isInstalled()) {
                    notInstalled = true
                    return
                }
                for (step in steps) {
                    indicator.checkCanceled()
                    indicator.text = stepTitle(step)
                    results += step to action(runner, step)
                }
            }

            override fun onSuccess() {
                if (notInstalled) LibmanNotifications.notInstalled(project)
            }

            override fun onThrowable(error: Throwable) {
                LOG.error("LibMan $title failed unexpectedly", error)
            }

            // Runs after onSuccess / onThrowable / cancel.
            override fun onFinished() {
                inFlight.release(key)
                if (results.isNotEmpty()) refresh(manifestDir)
                onDone(results.toList())
            }
        })
        return true
    }

    /** Refreshes the manifest folder so restored/removed files and the rewritten manifest show up. */
    private fun refresh(manifestDir: String) {
        val dir = LocalFileSystem.getInstance().refreshAndFindFileByPath(manifestDir) ?: return
        VfsUtil.markDirtyAndRefresh(true, true, true, dir)
    }
}
