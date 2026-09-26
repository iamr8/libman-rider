package com.github.iamr8.libman

import com.github.iamr8.libman.util.HttpValidators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpValidatorsTest {

    @Test fun `both headers`() {
        val v = HttpValidators.of("\"25e7\"", "Mon, 14 Sep 2026 17:07:37 GMT")!!
        assertEquals(
            mapOf("If-None-Match" to "\"25e7\"", "If-Modified-Since" to "Mon, 14 Sep 2026 17:07:37 GMT"),
            v.requestHeaders(),
        )
    }

    @Test fun `weak etag only`() {
        val v = HttpValidators.of("W/\"4fc4\"", null)!!
        assertEquals(mapOf("If-None-Match" to "W/\"4fc4\""), v.requestHeaders())
    }

    @Test fun `none or blank is null`() {
        assertNull(HttpValidators.of(null, null))
        assertNull(HttpValidators.of(" ", ""))
    }
}
