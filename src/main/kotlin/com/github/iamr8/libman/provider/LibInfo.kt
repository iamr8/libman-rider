package com.github.iamr8.libman.provider

/**
 * What a provider tells us about a library: its available versions and (when the provider exposes
 * it) a short description. Pure data, so the parsers stay unit-testable.
 */
data class LibInfo(
    val versions: List<String>,
    val description: String?,
)

/** A library name the provider's search returned, for completion. */
data class LibrarySuggestion(
    val name: String,
    val version: String?,
    val description: String?,
)

/** One library at one version, e.g. for its file list. */
data class LibraryVersionRef(
    val provider: String?,
    val name: String,
    val version: String,
)
