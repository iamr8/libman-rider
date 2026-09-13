package com.github.iamr8.libman.settings

/**
 * Output verbosity passed to the `libman` CLI (`--verbosity`). [NORMAL] is the CLI default, so it
 * emits no flag ([arg] is null); only [QUIET] and [DETAILED] add `--verbosity <id>`.
 */
enum class LibmanVerbosity(val id: String, private val display: String) {
    NORMAL("normal", "Normal"),
    QUIET("quiet", "Quiet"),
    DETAILED("detailed", "Detailed");

    /** The `--verbosity` argument value, or null when the default (no flag needed). */
    val arg: String? get() = if (this == NORMAL) null else id

    override fun toString(): String = display
}
