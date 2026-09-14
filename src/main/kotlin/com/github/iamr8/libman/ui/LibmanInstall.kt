package com.github.iamr8.libman.ui

import com.github.iamr8.libman.cli.DotnetTool
import com.github.iamr8.libman.cli.LibmanResult
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.nio.charset.StandardCharsets

/** Installs the LibMan CLI global tool via `dotnet tool install -g`, off the EDT. */
object LibmanInstall {

    private val LOG = Logger.getInstance(LibmanInstall::class.java)
    private const val TIMEOUT_MS = 3 * 60 * 1000

    /**
     * Run the install on a background thread, streaming output into the progress indicator.
     * [onDone] is called on the EDT with true when the CLI ends up installed (freshly or already).
     */
    fun installCli(project: Project?, onDone: (Boolean) -> Unit) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Installing LibMan CLI", true) {
            private var result: LibmanResult? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val commandLine = GeneralCommandLine(DotnetTool.installLibmanCli())
                    .withCharset(StandardCharsets.UTF_8)
                val handler = CapturingProcessHandler(commandLine)
                handler.addProcessListener(object : ProcessListener {
                    override fun onTextAvailable(
                        event: ProcessEvent,
                        outputType: com.intellij.openapi.util.Key<*>,
                    ) {
                        val line = event.text.trim()
                        if (line.isNotEmpty()) indicator.text2 = line
                    }
                })
                val out = handler.runProcess(TIMEOUT_MS)
                result = LibmanResult(out.exitCode, out.stdout, out.stderr, out.isTimeout)
            }

            override fun onSuccess() {
                val r = result ?: return finish(false)
                when {
                    r.ok -> {
                        LibmanNotifications.info(project, "LibMan", "LibMan CLI installed.")
                        finish(true)
                    }
                    DotnetTool.isAlreadyInstalled(r.combinedOutput()) -> {
                        LibmanNotifications.info(project, "LibMan", "LibMan CLI is already installed.")
                        finish(true)
                    }
                    else -> {
                        LibmanNotifications.failure(
                            project,
                            "Install LibMan CLI",
                            if (r.timedOut) "Install timed out." else "Could not install the LibMan CLI (is the .NET SDK on PATH?).",
                            r.combinedOutput(),
                        )
                        finish(false)
                    }
                }
            }

            override fun onThrowable(error: Throwable) {
                LOG.warn("dotnet tool install failed", error)
                LibmanNotifications.failure(
                    project,
                    "Install LibMan CLI",
                    "Could not run dotnet (is the .NET SDK on PATH?).",
                    error.message ?: error.toString(),
                )
                finish(false)
            }

            private fun finish(installed: Boolean) {
                ApplicationManager.getApplication().invokeLater { onDone(installed) }
            }
        })
    }
}
