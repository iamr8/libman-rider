package com.github.iamr8.libman.model

/**
 * What the user is typing in a `library` value, read from the text before the caret: a library
 * name (search the provider) or, after the version `@`, a version of a known name. The version
 * `@` is the last one, unless it is the leading scope marker (same rule as [LibraryId]).
 */
sealed interface LibraryInput {
    data class Name(val prefix: String) : LibraryInput
    data class Version(val name: String, val prefix: String) : LibraryInput

    companion object {
        fun parse(typed: String): LibraryInput {
            val at = typed.lastIndexOf('@')
            return if (at > 0) Version(typed.substring(0, at), typed.substring(at + 1)) else Name(typed)
        }
    }
}

/** Library names to offer in a `library` value. Pure for unit testing. */
object LibraryNameSuggestions {

    /** Letters needed before the provider is searched. */
    const val MIN_PREFIX = 3

    fun canSearch(prefix: String): Boolean = prefix.trim().length >= MIN_PREFIX

    /** The [items] whose name starts with [prefix] (case-insensitive), in their order. */
    fun <T> startingWith(items: List<T>, prefix: String, name: (T) -> String): List<T> =
        items.filter { name(it).startsWith(prefix.trim(), ignoreCase = true) }
}

/** Versions to offer after `@`: the newest first, pre-releases included. Pure for unit testing. */
object VersionSuggestions {

    const val LIMIT = 10

    /** The newest [limit] versions, newest first; unparseable versions are left out. */
    fun latest(versions: List<String>, limit: Int = LIMIT): List<String> =
        versions.asSequence()
            .mapNotNull { SemVer.parse(it) }
            .distinctBy { it.raw }
            .sortedDescending()
            .take(limit)
            .map { it.raw }
            .toList()
}

/** Checks on a library's `files` list against the files its provider has. Pure for unit testing. */
object ManifestFiles {

    /** True for a glob pattern (LibMan accepts them); those are not checked against the file list. */
    fun isPattern(path: String): Boolean = path.any { it == '*' || it == '?' || it == '[' || it == '{' }

    /** A provider file path in the form `files` uses: no leading `/`. */
    fun normalize(path: String): String = path.trim().removePrefix("/")

    /** The entries of [listed] that are not in [available] (patterns and blanks are skipped). */
    fun missing(listed: List<String>, available: Collection<String>): List<String> {
        val have = available.mapTo(HashSet()) { normalize(it) }
        return listed.filter { it.isNotBlank() && !isPattern(it) && normalize(it) !in have }
    }
}

/** Folders not offered as a `destination`. Pure for unit testing. */
object DestinationDirs {

    private val SKIPPED = setOf("node_modules", "bin", "obj")

    fun skip(name: String): Boolean = name.startsWith(".") || name.lowercase() in SKIPPED
}
