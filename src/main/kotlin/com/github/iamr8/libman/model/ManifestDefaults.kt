package com.github.iamr8.libman.model

/**
 * How a library entry falls back to the manifest root, the same way LibMan does
 * (`Manifest.UpdateLibraryProviderAndDestination` in aspnet/LibraryManager). Pure for unit testing.
 */
object ManifestDefaults {

    /** The entry's own `provider`, else the manifest `defaultProvider`. Only a missing value falls back. */
    fun provider(own: String?, default: String?): String? = own ?: default

    /**
     * The entry's own `destination`, else the manifest `defaultDestination` with its `[Name]` and
     * `[Version]` tokens expanded. `[Name]` is the last segment of a scoped or path name.
     */
    fun destination(own: String?, default: String?, name: String, version: String?): String? {
        if (own != null) return own
        if (default == null || '[' !in default) return default
        val shortName = name.substring(name.lastIndexOfAny(charArrayOf('/', '\\')) + 1)
        return default.replace("[Name]", shortName).replace("[Version]", version.orEmpty())
    }
}
