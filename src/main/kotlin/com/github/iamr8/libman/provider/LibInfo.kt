package com.github.iamr8.libman.provider

/**
 * What a provider tells us about a library: its available versions and (when the provider exposes
 * it) a short description. Pure data, so the parsers stay unit-testable.
 */
data class LibInfo(
    val versions: List<String>,
    val description: String?,
)
