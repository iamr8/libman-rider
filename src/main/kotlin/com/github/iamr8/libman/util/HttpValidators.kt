package com.github.iamr8.libman.util

/**
 * HTTP cache validators from a response (`ETag`, `Last-Modified`), sent back on the next request
 * of the same URL so the server can answer `304 Not Modified` instead of the full body.
 *
 * Pure (JDK only) for unit testing.
 */
data class HttpValidators(val etag: String?, val lastModified: String?) {

    /** True when there is nothing to send. */
    val isEmpty: Boolean get() = etag == null && lastModified == null

    /** The conditional request headers: `If-None-Match` and `If-Modified-Since`. */
    fun requestHeaders(): Map<String, String> = buildMap {
        etag?.let { put("If-None-Match", it) }
        lastModified?.let { put("If-Modified-Since", it) }
    }

    companion object {
        /** Validators from response header values; blank values count as absent. Null if both are absent. */
        fun of(etag: String?, lastModified: String?): HttpValidators? =
            HttpValidators(etag?.trim()?.ifEmpty { null }, lastModified?.trim()?.ifEmpty { null })
                .takeUnless { it.isEmpty }
    }
}
