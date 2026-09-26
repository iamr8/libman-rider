package com.github.iamr8.libman

import com.github.iamr8.libman.provider.CatalogCache
import com.github.iamr8.libman.provider.CatalogFetch
import com.github.iamr8.libman.provider.LibInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogCacheTest {

    private val jquery = LibInfo(listOf("3.7.1", "4.0.0"), "DOM library")

    private var clock = 1_000L
    private var calls = 0
    private var next: CatalogFetch = CatalogFetch.Found(jquery)

    private val cache = CatalogCache(
        fetch = { _, _ -> calls++; next },
        ttlMillis = { 60_000L },
        now = { clock },
    )

    @Test fun `nothing cached before the first fetch`() {
        assertNull(cache.info("cdnjs", "jquery"))
        assertNull(cache.error("cdnjs", "jquery"))
    }

    @Test fun `found result is cached while fresh`() {
        assertEquals(jquery, cache.getOrFetch("cdnjs", "jquery"))
        assertEquals(jquery, cache.getOrFetch("cdnjs", "jquery"))
        assertEquals(1, calls)
        assertEquals(jquery, cache.info("cdnjs", "jquery"))
        assertNull(cache.error("cdnjs", "jquery"))
    }

    @Test fun `failure is kept apart from no data`() {
        next = CatalogFetch.Failed("timed out")
        assertNull(cache.refresh("cdnjs", "jquery"))
        assertNull(cache.info("cdnjs", "jquery"))
        assertEquals("timed out", cache.error("cdnjs", "jquery"))
    }

    @Test fun `success after a failure clears the error`() {
        next = CatalogFetch.Failed("timed out")
        cache.refresh("cdnjs", "jquery")
        next = CatalogFetch.Found(jquery)
        assertEquals(jquery, cache.refresh("cdnjs", "jquery"))
        assertNull(cache.error("cdnjs", "jquery"))
    }

    @Test fun `refresh always fetches`() {
        cache.getOrFetch("cdnjs", "jquery")
        cache.refresh("cdnjs", "jquery")
        assertEquals(2, calls)
    }

    @Test fun `entries expire after the ttl`() {
        next = CatalogFetch.Failed("timed out")
        cache.refresh("cdnjs", "jquery")
        clock += 60_000L
        assertNull(cache.info("cdnjs", "jquery"))
        assertNull(cache.error("cdnjs", "jquery"))
        next = CatalogFetch.Found(jquery)
        assertEquals(jquery, cache.getOrFetch("cdnjs", "jquery"))
        assertEquals(2, calls)
    }

    @Test fun `key ignores provider case and blanks, not the name`() {
        cache.getOrFetch(" CDNJS ", "jquery")
        assertEquals(jquery, cache.info("cdnjs", "jquery"))
        assertNull(cache.info("cdnjs", "jQuery"))
    }
}
