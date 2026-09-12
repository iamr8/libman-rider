package com.github.iamr8.libman.cli

/**
 * Outcome of `libman update <lib>`. The CLI exits 0 even when it does nothing (no-op or
 * "no library found"), so the outcome must be read from stdout, not the exit code.
 */
sealed interface UpdateOutcome {
    /** The library was updated; [version] is what got installed. */
    data class Updated(val version: String) : UpdateOutcome
    /** Nothing to do - already on the newest version. */
    data object AlreadyLatest : UpdateOutcome
    /** The named library isn't in the manifest. */
    data object NotFound : UpdateOutcome
    /** Output didn't match a known shape (treat as a failure and show the raw output). */
    data object Unknown : UpdateOutcome
}

/** Outcome of `libman uninstall <lib>` (also exits 0 when the library isn't installed). */
sealed interface UninstallOutcome {
    data object Uninstalled : UninstallOutcome
    data object NotInstalled : UninstallOutcome
    data object Unknown : UninstallOutcome
}

/**
 * Parses the stdout of the mutating `libman` commands. Observed shapes (libman 3.x):
 *  - `Updated "jquery" to "4.0.0"`                      -> [UpdateOutcome.Updated]
 *  - `The library "jquery" is already up to date`       -> [UpdateOutcome.AlreadyLatest]
 *  - `No library found with name "x" to update.`        -> [UpdateOutcome.NotFound]
 *  - `Uninstalled library "jquery@4.0.0"`               -> [UninstallOutcome.Uninstalled]
 *  - `Library "x" is not installed. Nothing to uninstall` -> [UninstallOutcome.NotInstalled]
 *
 * Pure and platform-free for unit testing.
 */
object OpResultParser {

    private val UPDATED = Regex("""Updated\s+"[^"]*"\s+to\s+"([^"]+)"""", RegexOption.IGNORE_CASE)
    private val ALREADY = Regex("""already up to date""", RegexOption.IGNORE_CASE)
    private val NO_LIBRARY = Regex("""no library found""", RegexOption.IGNORE_CASE)
    private val UNINSTALLED = Regex("""uninstalled library""", RegexOption.IGNORE_CASE)
    private val NOT_INSTALLED = Regex("""is not installed|nothing to uninstall""", RegexOption.IGNORE_CASE)

    fun parseUpdate(stdout: String): UpdateOutcome {
        for (line in stdout.lineSequence()) {
            UPDATED.find(line)?.let { return UpdateOutcome.Updated(it.groupValues[1].trim()) }
            if (ALREADY.containsMatchIn(line)) return UpdateOutcome.AlreadyLatest
            if (NO_LIBRARY.containsMatchIn(line)) return UpdateOutcome.NotFound
        }
        return UpdateOutcome.Unknown
    }

    fun parseUninstall(stdout: String): UninstallOutcome {
        for (line in stdout.lineSequence()) {
            if (UNINSTALLED.containsMatchIn(line)) return UninstallOutcome.Uninstalled
            if (NOT_INSTALLED.containsMatchIn(line)) return UninstallOutcome.NotInstalled
        }
        return UninstallOutcome.Unknown
    }
}
