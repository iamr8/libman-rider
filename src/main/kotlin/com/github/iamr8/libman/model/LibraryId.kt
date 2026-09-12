package com.github.iamr8.libman.model

/**
 * A parsed `library` value from a `libman.json` entry.
 *
 * LibMan library ids take a few shapes:
 *  - `jquery@3.6.0`            -> name `jquery`, version `3.6.0`
 *  - `jquery`                  -> name `jquery`, no version
 *  - `@microsoft/signalr@8.0`  -> scoped npm, name `@microsoft/signalr`, version `8.0`
 *  - `@popperjs/core`          -> scoped npm, no version
 *  - `jquery/jquery@3.6.0`     -> jsDelivr GitHub form, name `jquery/jquery`, version `3.6.0`
 *  - a file-system path        -> the whole string is the "name"; there is no version
 *
 * The version is whatever follows the LAST `@`, unless that `@` is the leading scope marker
 * (position 0) or the entry uses the `filesystem` provider (paths are never versioned).
 *
 * Kept free of any platform dependency so it can be unit-tested.
 */
data class LibraryId(
    /** The original, unparsed `library` string. */
    val raw: String,
    /** The library name without the version (what the `libman` CLI expects as its argument). */
    val name: String,
    /** The version part, or `null` when the entry pins no version. */
    val version: String?,
) {
    /** True when this entry has no meaningful version to check or update (e.g. a filesystem path). */
    val isVersionless: Boolean get() = version == null

    companion object {
        const val FILESYSTEM_PROVIDER = "filesystem"

        /**
         * Parses a `library` string. [provider] is the entry's effective provider
         * (its own `provider`, else the manifest `defaultProvider`); the `filesystem` provider
         * is treated as an unversioned path.
         */
        fun parse(raw: String, provider: String?): LibraryId {
            val trimmed = raw.trim()
            if (provider?.trim()?.lowercase() == FILESYSTEM_PROVIDER) {
                return LibraryId(raw, trimmed, null)
            }
            val at = trimmed.lastIndexOf('@')
            return if (at > 0) {
                LibraryId(raw, trimmed.substring(0, at), trimmed.substring(at + 1).ifBlank { null })
            } else {
                LibraryId(raw, trimmed, null)
            }
        }
    }
}
