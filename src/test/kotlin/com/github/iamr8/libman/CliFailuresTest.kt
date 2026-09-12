package com.github.iamr8.libman

import com.github.iamr8.libman.cli.CliFailures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CliFailuresTest {

    @Test fun `lib error code line is unwrapped`() {
        // Verbatim from `libman install jquery@99.99.99` (libman 3.0.114).
        val out = "[LIB002]: The \"jquery@99.99.99\" library could not be resolved by the \"cdnjs\" provider"
        assertEquals(
            "The \"jquery@99.99.99\" library could not be resolved by the \"cdnjs\" provider",
            CliFailures.describe("install", 1, "", out, timedOut = false),
        )
    }

    @Test fun `missing manifest is summarised`() {
        val out = "libman.json was not found:/tmp/empty/libman.json"
        assertEquals(
            "No libman.json found for this operation.",
            CliFailures.describe("restore", 1, out, "", timedOut = false),
        )
    }

    @Test fun `could-not-resolve without code is kept`() {
        val out = "The \"bootstrap\" library could not be resolved by the \"unpkg\" provider"
        assertEquals(out, CliFailures.describe("update", 1, "", out, timedOut = false))
    }

    @Test fun `timeout message`() {
        assertEquals("LibMan restore timed out.", CliFailures.describe("restore", -1, "", "", timedOut = true))
    }

    @Test fun `falls back to first line`() {
        assertEquals("Something odd happened", CliFailures.describe("update", 1, "Something odd happened\nmore", "", timedOut = false))
    }

    @Test fun `generic fallback includes exit code when no output`() {
        val msg = CliFailures.describe("clean", 7, "", "", timedOut = false)
        assertTrue(msg, msg.contains("exit code 7"))
    }
}
