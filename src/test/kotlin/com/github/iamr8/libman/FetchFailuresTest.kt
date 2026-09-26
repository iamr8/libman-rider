package com.github.iamr8.libman

import com.github.iamr8.libman.provider.FetchFailures
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class FetchFailuresTest {

    @Test fun `status codes`() {
        assertEquals("not found on the provider", FetchFailures.forStatus(404))
        assertEquals("rate limited by the provider (HTTP 429)", FetchFailures.forStatus(429))
        assertEquals("provider error (HTTP 503)", FetchFailures.forStatus(503))
        assertEquals("HTTP 403", FetchFailures.forStatus(403))
    }

    @Test fun `network errors`() {
        assertEquals("timed out", FetchFailures.describe(SocketTimeoutException("Read timed out")))
        assertEquals("no network (host not found)", FetchFailures.describe(UnknownHostException("api.cdnjs.com")))
        assertEquals("cannot connect", FetchFailures.describe(ConnectException("Connection refused")))
        assertEquals("secure connection failed", FetchFailures.describe(SSLHandshakeException("bad cert")))
    }

    @Test fun `other errors use their message, else the type`() {
        assertEquals("stream closed", FetchFailures.describe(IOException("stream closed")))
        assertEquals("IOException", FetchFailures.describe(IOException()))
        assertEquals("IOException", FetchFailures.describe(IOException("  ")))
    }
}
