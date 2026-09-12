package com.github.iamr8.libman

import com.github.iamr8.libman.cli.OpResultParser
import com.github.iamr8.libman.cli.UninstallOutcome
import com.github.iamr8.libman.cli.UpdateOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class OpResultParserTest {

    // --- update (all verbatim from libman 3.0.114) ---

    @Test fun `update succeeded`() {
        val out = buildString {
            appendLine("Restoring library jquery@4.0.0...")
            appendLine("Updated \"jquery\" to \"4.0.0\"")
        }
        assertEquals(UpdateOutcome.Updated("4.0.0"), OpResultParser.parseUpdate(out))
    }

    @Test fun `update no-op is not treated as success`() {
        val out = "The library \"jquery\" is already up to date"
        assertEquals(UpdateOutcome.AlreadyLatest, OpResultParser.parseUpdate(out))
    }

    @Test fun `update of missing library is NotFound`() {
        val out = "No library found with name \"doesnotexist\" to update."
        assertEquals(UpdateOutcome.NotFound, OpResultParser.parseUpdate(out))
    }

    @Test fun `update unknown output`() {
        assertEquals(UpdateOutcome.Unknown, OpResultParser.parseUpdate("weird"))
    }

    // --- uninstall ---

    @Test fun `uninstall succeeded`() {
        val out = "Uninstalled library \"jquery@4.0.0\""
        assertEquals(UninstallOutcome.Uninstalled, OpResultParser.parseUninstall(out))
    }

    @Test fun `uninstall of missing library is NotInstalled`() {
        val out = "Library \"doesnotexist9\" is not installed. Nothing to uninstall"
        assertEquals(UninstallOutcome.NotInstalled, OpResultParser.parseUninstall(out))
    }

    @Test fun `uninstall unknown output`() {
        assertEquals(UninstallOutcome.Unknown, OpResultParser.parseUninstall(""))
    }
}
