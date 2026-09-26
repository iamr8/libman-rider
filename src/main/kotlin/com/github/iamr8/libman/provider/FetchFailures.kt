package com.github.iamr8.libman.provider

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Short, user-facing reasons for a failed catalog lookup. Pure (JDK only) for unit testing. */
object FetchFailures {

    const val UNEXPECTED_RESPONSE = "unexpected response from the provider"

    /** Reason for a non-2xx HTTP status. */
    fun forStatus(code: Int): String = when {
        code == 404 -> "not found on the provider"
        code == 429 -> "rate limited by the provider (HTTP 429)"
        code >= 500 -> "provider error (HTTP $code)"
        else -> "HTTP $code"
    }

    /** Reason for a network or I/O error. */
    fun describe(e: Throwable): String = when (e) {
        is SocketTimeoutException -> "timed out"
        is UnknownHostException -> "no network (host not found)"
        is ConnectException -> "cannot connect"
        is SSLException -> "secure connection failed"
        else -> e.message?.trim()?.takeIf { it.isNotEmpty() } ?: e.javaClass.simpleName
    }
}
