package com.github.iamr8.libman.cli

/**
 * Pure builders for `libman` command lines. Each returns the full argument list
 * (executable first). Kept side-effect-free so it can be unit-tested.
 *
 * The `libman` CLI resolves `libman.json` from the current working directory, so the caller
 * (see [LibmanRunner]) is responsible for running these with the manifest's folder as the
 * working directory. These builders never encode a path.
 *
 * [verbosity] is the `--verbosity` value ("quiet" / "detailed"); pass null (the default) to omit
 * the flag, which is what "normal" maps to (see [com.github.iamr8.libman.settings.LibmanVerbosity]).
 */
object LibmanCommand {

    /** `libman --version` — used to check the tool is installed. */
    fun version(libmanPath: String): List<String> =
        listOf(libmanPath, "--version")

    /** `libman restore` — download every library defined in the manifest. */
    fun restore(libmanPath: String, verbosity: String? = null): List<String> =
        buildList { add(libmanPath); add("restore"); verbosity(verbosity) }

    /** `libman clean` — delete files previously restored (manifest entries stay). */
    fun clean(libmanPath: String, verbosity: String? = null): List<String> =
        buildList { add(libmanPath); add("clean"); verbosity(verbosity) }

    /** `libman uninstall <name>` — remove a library's files and its manifest entry. */
    fun uninstall(libmanPath: String, name: String, verbosity: String? = null): List<String> =
        buildList { add(libmanPath); add("uninstall"); add(name); verbosity(verbosity) }

    /**
     * `libman update <name> [--pre] [--to <version>] [--verbosity <v>]` — update a library.
     * With neither `--pre` nor `--to` it moves to the latest stable version.
     */
    fun update(
        libmanPath: String,
        name: String,
        pre: Boolean = false,
        to: String? = null,
        verbosity: String? = null,
    ): List<String> =
        buildList {
            add(libmanPath); add("update"); add(name)
            if (pre) add("--pre")
            if (!to.isNullOrBlank()) { add("--to"); add(to) }
            verbosity(verbosity)
        }

    /** Append `--verbosity <v>` when [v] is set; omitted for the default (null/blank). */
    private fun MutableList<String>.verbosity(v: String?) {
        if (!v.isNullOrBlank()) { add("--verbosity"); add(v) }
    }
}
