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

    @Test fun `whatif latest`() {
        assertEquals(listOf(libman, "update", "jquery", "--whatif"), LibmanCommand.whatIf(libman, "jquery"))
    }

    @Test fun `whatif prerelease`() {
        assertEquals(
            listOf(libman, "update", "jquery", "--whatif", "--pre"),
            LibmanCommand.whatIf(libman, "jquery", pre = true),
        )
    }
}
