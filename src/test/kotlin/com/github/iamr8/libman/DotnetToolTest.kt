package com.github.iamr8.libman

import com.github.iamr8.libman.cli.DotnetTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DotnetToolTest {

    @Test fun `install command uses the global flag and CLI package`() {
        assertEquals(
            listOf("dotnet", "tool", "install", "-g", "Microsoft.Web.LibraryManager.Cli"),
            DotnetTool.installLibmanCli(),
        )
    }

    @Test fun `install command honors a custom dotnet path`() {
        assertEquals(
            listOf("/usr/local/bin/dotnet", "tool", "install", "-g", "Microsoft.Web.LibraryManager.Cli"),
            DotnetTool.installLibmanCli("/usr/local/bin/dotnet"),
        )
    }

    @Test fun `already-installed output is detected`() {
        assertTrue(DotnetTool.isAlreadyInstalled("Tool 'microsoft.web.librarymanager.cli' is already installed."))
        assertTrue(DotnetTool.isAlreadyInstalled("... ALREADY INSTALLED ..."))
    }

    @Test fun `unrelated output is not treated as already-installed`() {
        assertFalse(DotnetTool.isAlreadyInstalled("You can invoke the tool using the following command: libman"))
        assertFalse(DotnetTool.isAlreadyInstalled(""))
    }
}
