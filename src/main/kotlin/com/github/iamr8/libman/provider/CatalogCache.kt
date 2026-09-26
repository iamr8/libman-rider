package com.github.iamr8.libman.provider

import java.util.concurrent.ConcurrentHashMap

/** The outcome of one catalog lookup: the library's info, or why the lookup failed. */
sealed interface CatalogFetch {
    data class Found(val info: LibInfo) : CatalogFetch
    data class Failed(val reason: String) : CatalogFetch
}

/**
 * Catalog lookups cached per `(provider, name)`, with an expiry. A failed lookup is stored as an
 * error next to the (absent) data, so the UI can tell "the lookup failed" apart from "no update".
 *
 * Pure (no platform): the fetch, the expiry and the clock are passed in, for unit testing.
 * [getOrFetch] and [refresh] block on [fetch]; call them off the EDT.
 */
class CatalogCache(
    private val fetch: (provider: String?, name: String) -> CatalogFetch,
    private val ttlMillis: () -> Long,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private data class Entry(val info: LibInfo?, val error: String?, val at: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    /** Fresh cached info, or null if missing, expired, or the last lookup failed. Never fetches. */
    fun info(provider: String?, name: String): LibInfo? = fresh(provider, name)?.info

    /** Why the last lookup failed, or null if it succeeded, is missing or expired. Never fetches. */
    fun error(provider: String?, name: String): String? = fresh(provider, name)?.error

    /** Cached-if-fresh, else fetch now (blocking). */
    fun getOrFetch(provider: String?, name: String): LibInfo? {
        fresh(provider, name)?.let { return it.info }
        return refresh(provider, name)
    }

    /** Fetch now (blocking) and replace the cached entry. */
    fun refresh(provider: String?, name: String): LibInfo? {
        val entry = when (val r = fetch(provider, name)) {
            is CatalogFetch.Found -> Entry(r.info, null, now())
            is CatalogFetch.Failed -> Entry(null, r.reason, now())
        }
        entries[key(provider, name)] = entry
        return entry.info
    }

    private fun fresh(provider: String?, name: String): Entry? =
        entries[key(provider, name)]?.takeIf { now() - it.at < ttlMillis() }

    private fun key(provider: String?, name: String): String =
        "${provider?.trim()?.lowercase().orEmpty()}::$name"
}
