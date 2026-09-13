package com.github.iamr8.libman

import com.github.iamr8.libman.cli.LibmanCommand
import org.junit.Assert.assertEquals
import org.junit.Test

class LibmanCommandTest {

    private val libman = "/usr/bin/libman"

    @Test fun version() {
        assertEquals(listOf(libman, "--version"), LibmanCommand.version(libman))
    }

    @Test fun restore() {
        assertEquals(listOf(libman, "restore"), LibmanCommand.restore(libman))
    }

    @Test fun clean() {
        assertEquals(listOf(libman, "clean"), LibmanCommand.clean(libman))
    }

    @Test fun uninstall() {
        assertEquals(listOf(libman, "uninstall", "jquery"), LibmanCommand.uninstall(libman, "jquery"))
    }

    @Test fun `update to latest`() {
        assertEquals(listOf(libman, "update", "jquery"), LibmanCommand.update(libman, "jquery"))
    }

    @Test fun `update prerelease`() {
        assertEquals(listOf(libman, "update", "jquery", "--pre"), LibmanCommand.update(libman, "jquery", pre = true))
    }

    @Test fun `update to specific version`() {
        assertEquals(
            listOf(libman, "update", "jquery", "--to", "3.7.1"),
            LibmanCommand.update(libman, "jquery", to = "3.7.1"),
        )
    }

    @Test fun `update ignores blank to`() {
        assertEquals(listOf(libman, "update", "jquery"), LibmanCommand.update(libman, "jquery", to = "  "))
    }

    // --- verbosity ---

    @Test fun `no verbosity flag by default`() {
        assertEquals(listOf(libman, "restore"), LibmanCommand.restore(libman))
        assertEquals(listOf(libman, "clean"), LibmanCommand.clean(libman))
        assertEquals(listOf(libman, "uninstall", "jquery"), LibmanCommand.uninstall(libman, "jquery"))
        assertEquals(listOf(libman, "update", "jquery"), LibmanCommand.update(libman, "jquery"))
    }

    @Test fun `blank verbosity is omitted`() {
        assertEquals(listOf(libman, "restore"), LibmanCommand.restore(libman, verbosity = "  "))
    }

    @Test fun `verbosity appended to restore`() {
        assertEquals(listOf(libman, "restore", "--verbosity", "detailed"), LibmanCommand.restore(libman, "detailed"))
    }

    @Test fun `verbosity appended to clean`() {
        assertEquals(listOf(libman, "clean", "--verbosity", "quiet"), LibmanCommand.clean(libman, "quiet"))
    }

    @Test fun `verbosity appended to uninstall`() {
        assertEquals(
            listOf(libman, "uninstall", "jquery", "--verbosity", "quiet"),
            LibmanCommand.uninstall(libman, "jquery", "quiet"),
        )
    }

    @Test fun `verbosity appended after update flags`() {
        assertEquals(
            listOf(libman, "update", "jquery", "--pre", "--to", "3.7.1", "--verbosity", "detailed"),
            LibmanCommand.update(libman, "jquery", pre = true, to = "3.7.1", verbosity = "detailed"),
        )
    }
}
