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

    /**
     * The libman executable to run: [customPath] when it points at an executable, else the first
     * existing well-known candidate, else the bare `libman` command as a last resort (relies on the
     * process PATH).
     */
    fun resolve(customPath: String? = null): String =
        resolveExisting(customPath) ?: exeName()

    /**
     * The libman executable if one actually exists (custom path, PATH, or `~/.dotnet/tools`), or
     * null when only the bare-command fallback remains. Lets callers decide whether an install check
     * needs an actual process spawn.
     */
    fun resolveExisting(customPath: String? = null): String? {
        if (!customPath.isNullOrBlank()) {
            val f = File(customPath.trim())
            if (f.canExecute()) return f.path
        }
        return candidates.firstOrNull { it.isNotBlank() && File(it).canExecute() }
    }
}
