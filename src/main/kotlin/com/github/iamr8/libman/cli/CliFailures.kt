package com.github.iamr8.libman.cli

/**
 * Turns raw `libman` output into one short, user-facing sentence for a notification.
 * The full output stays available behind a "Copy Details" action; this is only the summary.
 *
 * Pure and platform-free for unit testing. Observed libman 3.x shapes:
 *  - `[LIB002]: The "jquery@9.9.9" library could not be resolved by the "cdnjs" provider`
 *  - `libman.json was not found:/path/to/libman.json`
 */
object CliFailures {

    /** The command that installs the LibMan CLI global tool. */
    const val INSTALL_COMMAND = "dotnet tool install -g Microsoft.Web.LibraryManager.Cli"

    /** Message shown when the CLI itself is missing. */
    const val NOT_INSTALLED = "LibMan CLI not found. Install it with: $INSTALL_COMMAND"

    private val LIB_CODE_LINE = Regex("""\[LIB\d+]:?\s*(.*)""")

    /**
     * @param op a short verb for the failed operation, e.g. "restore" or "update".
     */
    fun describe(op: String, exitCode: Int, stdout: String, stderr: String, timedOut: Boolean): String {
        if (timedOut) return "LibMan $op timed out."

        val lines = (stderr + "\n" + stdout).lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        // A `[LIBxxx]` diagnostic is the most specific signal — prefer it.
        lines.firstNotNullOfOrNull { LIB_CODE_LINE.matchEntire(it)?.groupValues?.get(1)?.ifBlank { null } }
            ?.let { return it }

        lines.firstOrNull { it.startsWith("libman.json was not found") }
            ?.let { return "No libman.json found for this operation." }

        lines.firstOrNull { it.contains("could not be resolved", ignoreCase = true) }
            ?.let { return it }

        // Fall back to the first meaningful line, else a generic message with the exit code.
        return lines.firstOrNull() ?: "LibMan $op failed (exit code $exitCode)."
    }
}
