package com.github.iamr8.libman.cli

import java.io.File

/**
 * Finds the `libman` executable. LibMan is a .NET global tool, so it normally lives in
 * `~/.dotnet/tools`. GUI apps on macOS often don't inherit the shell PATH, so fall back to
 * well-known install locations before giving up on the bare command.
 */
object LibmanLocator {

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().startsWith("Windows")

    private fun exeName(): String = if (isWindows()) "libman.exe" else "libman"

    private val candidates: List<String> = buildList {
        System.getenv("PATH")?.split(File.pathSeparator)?.forEach { dir ->
            if (dir.isNotBlank()) add(File(dir, exeName()).path)
        }
        // Default .NET global-tools location.
        System.getProperty("user.home")?.let { add(File(it, ".dotnet/tools/${exeName()}").path) }
    }

    /** First existing candidate, or the bare `libman` command as a last resort. */
    fun resolve(): String =
        candidates.firstOrNull { it.isNotBlank() && File(it).canExecute() } ?: exeName()
}
