package com.github.iamr8.libman.cli

/**
 * Pure builders for `libman` command lines. Each returns the full argument list
 * (executable first). Kept side-effect-free so it can be unit-tested.
 *
 * The `libman` CLI resolves `libman.json` from the current working directory, so the caller
 * (see [LibmanRunner]) is responsible for running these with the manifest's folder as the
 * working directory. These builders never encode a path.
 */
object LibmanCommand {

    /** `libman --version` — used to check the tool is installed. */
    fun version(libmanPath: String): List<String> =
        listOf(libmanPath, "--version")

    /** `libman restore` — download every library defined in the manifest. */
    fun restore(libmanPath: String): List<String> =
        listOf(libmanPath, "restore")

    /** `libman clean` — delete files previously restored (manifest entries stay). */
    fun clean(libmanPath: String): List<String> =
        listOf(libmanPath, "clean")

    /** `libman uninstall <name>` — remove a library's files and its manifest entry. */
    fun uninstall(libmanPath: String, name: String): List<String> =
        listOf(libmanPath, "uninstall", name)

    /**
     * `libman update <name> [--pre] [--to <version>]` — update a library.
     * With neither flag it moves to the latest stable version.
     */
    fun update(libmanPath: String, name: String, pre: Boolean = false, to: String? = null): List<String> =
        buildList {
            add(libmanPath); add("update"); add(name)
            if (pre) add("--pre")
            if (!to.isNullOrBlank()) { add("--to"); add(to) }
        }

    /**
     * `libman update <name> --whatif [--pre]` — read-only "what would happen".
     * Prints the target version (or "already up to date") without changing anything.
     */
    fun whatIf(libmanPath: String, name: String, pre: Boolean = false): List<String> =
        buildList {
            add(libmanPath); add("update"); add(name); add("--whatif")
            if (pre) add("--pre")
        }
}
