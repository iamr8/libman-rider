package com.github.iamr8.libman.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.util.Key
import java.nio.charset.StandardCharsets

/** Raw result of a `libman` process run. */
data class LibmanResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
) {
    val ok: Boolean get() = exitCode == 0 && !timedOut

    /** stderr and stdout joined — for the notification's "Copy Details" action. */
    fun combinedOutput(): String = buildString {
        if (stdout.isNotBlank()) append(stdout)
        if (stderr.isNotBlank()) {
            if (isNotEmpty()) append('\n')
            append(stderr)
        }
    }.ifBlank { "(no output)" }
}

/**
 * Runs the `libman` CLI. Blocking — call from a background thread.
 *
 * Every command runs with the working directory set to the manifest's own folder, because
 * `libman` resolves `libman.json` from the current directory. [CapturingProcessHandler] does not
 * feed the child's stdin, so the rare "choose one" interactive prompt (two libraries sharing a
 * name across providers) cannot hang the process.
 *
 * @param libmanPath the executable to run (see [LibmanLocator.resolve]).
 * @param verbosity the `--verbosity` value, or null for the CLI default (see [LibmanCommand]).
 * @param onLine called with each non-blank output line as it arrives, for a live progress display.
 */
class LibmanRunner(
    private val libmanPath: String = LibmanLocator.resolve(),
    private val verbosity: String? = null,
    private val onLine: ((String) -> Unit)? = null,
) {
    /** True if the `libman` global tool is installed and runnable. */
    fun isInstalled(timeoutMs: Int = 30_000): Boolean = try {
        run(LibmanCommand.version(libmanPath), workDir = null, timeoutMs = timeoutMs).ok
    } catch (e: Exception) {
        false
    }

    fun update(manifestDir: String, name: String, pre: Boolean = false, to: String? = null): LibmanResult =
        run(LibmanCommand.update(libmanPath, name, pre, to, verbosity), manifestDir, NETWORK_TIMEOUT_MS)

    fun uninstall(manifestDir: String, name: String): LibmanResult =
        run(LibmanCommand.uninstall(libmanPath, name, verbosity), manifestDir, NETWORK_TIMEOUT_MS)

    fun restore(manifestDir: String): LibmanResult =
        run(LibmanCommand.restore(libmanPath, verbosity), manifestDir, NETWORK_TIMEOUT_MS)

    fun clean(manifestDir: String): LibmanResult =
        run(LibmanCommand.clean(libmanPath, verbosity), manifestDir, NETWORK_TIMEOUT_MS)

    private fun run(cmd: List<String>, workDir: String?, timeoutMs: Int): LibmanResult {
        val commandLine = GeneralCommandLine(cmd).withCharset(StandardCharsets.UTF_8)
        if (workDir != null) commandLine.setWorkDirectory(workDir)
        val handler = CapturingProcessHandler(commandLine)
        onLine?.let { cb ->
            handler.addProcessListener(object : ProcessAdapter() {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    val line = event.text.trim()
                    if (line.isNotEmpty()) cb(line)
                }
            })
        }
        val out = handler.runProcess(timeoutMs)
        return LibmanResult(out.exitCode, out.stdout, out.stderr, out.isTimeout)
    }

    private companion object {
        // libman downloads from a CDN; allow generous time for large libraries / slow networks.
        const val NETWORK_TIMEOUT_MS = 5 * 60 * 1000
    }
}
