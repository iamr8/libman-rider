package com.github.iamr8.libman.cli

/** Outcome of a `libman update <lib> --whatif` run. */
sealed interface WhatIfResult {
    /** The installed version is already the newest. */
    data object UpToDate : WhatIfResult

    /** A newer version is available; [version] is what an update would install. */
    data class WouldUpdate(val version: String) : WhatIfResult

    /** The output didn't match a known shape (treat as a failure and surface the raw output). */
    data object Unknown : WhatIfResult
}

/**
 * Parses the stdout of `libman update <lib> --whatif`. Observed shapes (libman 3.x):
 *  - `Library "jquery" would be updated to latest version "4.0.0"`  -> [WhatIfResult.WouldUpdate]
 *  - `The library "jquery" is already up to date`                   -> [WhatIfResult.UpToDate]
 *
 * Pure and platform-free for unit testing.
 */
object WhatIfParser {

    // The version follows `version "<here>"` on a "would be updated" line. Allow "latest" /
    // "latest prerelease" between "to" and "version" so prerelease output parses too.
    private val WOULD_UPDATE = Regex("""would be updated to .*?version\s+"([^"]+)"""", RegexOption.IGNORE_CASE)
    private val UP_TO_DATE = Regex("""already up to date""", RegexOption.IGNORE_CASE)

    fun parse(stdout: String): WhatIfResult {
        for (line in stdout.lineSequence()) {
            WOULD_UPDATE.find(line)?.let { return WhatIfResult.WouldUpdate(it.groupValues[1].trim()) }
            if (UP_TO_DATE.containsMatchIn(line)) return WhatIfResult.UpToDate
        }
        return WhatIfResult.Unknown
    }
}
