package com.github.iamr8.libman.model

/**
 * Pure validation rules for the `libman.json` manifest itself (not the library versions):
 * the root schema `version` and provider names. Platform-free for unit testing.
 *
 * Facts from the authoritative schema (https://www.schemastore.org/libman):
 * `version` is one of "1.0" / "3.0" (latest "3.0"); provider names are free strings, but LibMan's
 * built-in providers are cdnjs, jsdelivr, unpkg, filesystem.
 */
object ManifestRules {

    val KNOWN_PROVIDERS = setOf("cdnjs", "jsdelivr", "unpkg", "filesystem")
    const val LATEST_VERSION = "3.0"
    val KNOWN_VERSIONS = setOf("1.0", "3.0")

    enum class Level { INFO, WARNING }

    /**
     * A problem message for a provider value, or null if it is fine.
     * Null/blank is fine (a library inherits `defaultProvider`); a known name is fine.
     */
    fun providerProblem(raw: String?): String? {
        val v = raw?.trim()?.lowercase().orEmpty()
        if (v.isEmpty() || v in KNOWN_PROVIDERS) return null
        return "Unknown LibMan provider '${raw?.trim()}'. Known: cdnjs, jsdelivr, unpkg, filesystem."
    }

    /**
     * A problem for the root schema `version`, or null if it is the latest.
     * An older-but-known version is INFO ("a newer schema exists"); an unknown one is WARNING.
     */
    fun versionProblem(raw: String?): Pair<Level, String>? {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty() || v == LATEST_VERSION) return null
        return if (v in KNOWN_VERSIONS) {
            Level.INFO to "A newer libman.json schema is available (version $LATEST_VERSION)."
        } else {
            Level.WARNING to "Unknown libman.json schema version '$v'. Known: 1.0, 3.0."
        }
    }
}
