package com.github.iamr8.libman

import com.github.iamr8.libman.cli.LibmanResult
import com.github.iamr8.libman.cli.PendingSummary
import com.github.iamr8.libman.model.PendingChange.Remove
import com.github.iamr8.libman.model.PendingChange.Update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingSummaryTest {

    private fun ok(stdout: String) = LibmanResult(0, stdout, "", timedOut = false)

    @Test fun `successful batch`() {
        val s = PendingSummary.of(
            listOf(
                Update("jquery", "4.0.0") to ok("Updated \"jquery\" to \"4.0.0\""),
                Update("vue", "3.5.0") to ok("The library \"vue\" is already up to date"),
                Remove("lodash") to ok("Uninstalled library \"lodash@4.17.21\""),
            ),
        )
        assertEquals(
            listOf("jquery: Updated to 4.0.0.", "vue: Already up to date.", "lodash: Uninstalled."),
            s.applied,
        )
        assertTrue(s.failed.isEmpty())
        assertEquals("", s.details)
    }

    @Test fun `exit 0 outcomes that are failures`() {
        val s = PendingSummary.of(
            listOf(
                Update("nope", "1.0.0") to ok("No library found with name \"nope\" to update."),
                Remove("gone") to ok("Library \"gone\" is not installed. Nothing to uninstall"),
                Update("odd", "1.0.0") to ok("something new"),
            ),
        )
        assertTrue(s.applied.isEmpty())
        assertEquals(
            listOf(
                "nope: No library named \"nope\" in the manifest.",
                "gone: \"gone\" is not installed.",
                "odd: Update result was unclear.",
            ),
            s.failed,
        )
    }

    @Test fun `cli failure uses the libman diagnostic and keeps the output`() {
        val failed = LibmanResult(1, "", "[LIB002]: The \"jquery@9.9.9\" library could not be resolved", timedOut = false)
        val s = PendingSummary.of(
            listOf(
                Update("jquery", "9.9.9") to failed,
                Remove("lodash") to ok("Uninstalled library \"lodash@4.17.21\""),
            ),
        )
        assertEquals(listOf("lodash: Uninstalled."), s.applied)
        assertEquals(listOf("jquery: The \"jquery@9.9.9\" library could not be resolved"), s.failed)
        assertTrue(s.details.startsWith("jquery:\n"))
        assertTrue(s.details.contains("[LIB002]"))
    }
}
