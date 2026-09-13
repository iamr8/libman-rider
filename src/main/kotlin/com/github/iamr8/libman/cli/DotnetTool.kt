package com.github.iamr8.libman.cli

/**
 * Pure helpers for the `dotnet tool` command that installs the LibMan CLI global tool.
 * The CLI (`Microsoft.Web.LibraryManager.Cli`) is what this plugin runs; a global install is
 * machine-wide, so no project needs to be chosen.
 */
object DotnetTool {

    /** The NuGet package id of the LibMan CLI global tool. */
    const val LIBMAN_CLI_PACKAGE = "Microsoft.Web.LibraryManager.Cli"

    /** `dotnet tool install -g Microsoft.Web.LibraryManager.Cli`. */
    fun installLibmanCli(dotnetPath: String = "dotnet"): List<String> =
        listOf(dotnetPath, "tool", "install", "-g", LIBMAN_CLI_PACKAGE)

    /**
     * True when the tool output reports the tool is already installed. `dotnet tool install`
     * exits non-zero in that case, but it is not a real failure - report it as info.
     */
    fun isAlreadyInstalled(output: String): Boolean =
        output.contains("already installed", ignoreCase = true)
}
