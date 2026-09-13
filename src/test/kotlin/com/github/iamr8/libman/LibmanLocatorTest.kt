package com.github.iamr8.libman

import com.github.iamr8.libman.cli.LibmanLocator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class LibmanLocatorTest {

    private fun tempExecutable(): File =
        File.createTempFile("libman-test", "").apply {
            deleteOnExit()
            setExecutable(true)
        }

    @Test fun `custom path is used when it is executable`() {
        val exe = tempExecutable()
        assertEquals(exe.path, LibmanLocator.resolveExisting(exe.path))
        assertEquals(exe.path, LibmanLocator.resolve(exe.path))
    }

    @Test fun `custom path is trimmed`() {
        val exe = tempExecutable()
        assertEquals(exe.path, LibmanLocator.resolveExisting("  ${exe.path}  "))
    }

    @Test fun `nonexistent custom path is not used`() {
        val bogus = "/no/such/place/libman-xyz"
        // Falls through to auto-detect, so it must never echo the bogus path back.
        assertNotEquals(bogus, LibmanLocator.resolve(bogus))
        assertNotEquals(bogus, LibmanLocator.resolveExisting(bogus))
    }

    @Test fun `non-executable custom path is ignored`() {
        val plain = File.createTempFile("libman-test", "").apply {
            deleteOnExit()
            setExecutable(false)
        }
        assertNotEquals(plain.path, LibmanLocator.resolveExisting(plain.path))
    }

    @Test fun `resolve falls back to bare command name`() {
        // With no custom path and (on a clean CI box) no installed libman, resolveExisting is null
        // and resolve returns the bare command; assert resolve never returns null / empty.
        assert(LibmanLocator.resolve("").isNotEmpty())
    }

    @Test fun `blank custom path resolves same as none`() {
        assertEquals(LibmanLocator.resolveExisting(null), LibmanLocator.resolveExisting("   "))
    }

    @Test fun `nonexistent custom path resolveExisting is null on a box without libman`() {
        // Only assert the null case when auto-detect also finds nothing, to stay deterministic.
        val auto = LibmanLocator.resolveExisting(null)
        if (auto == null) assertNull(LibmanLocator.resolveExisting("/no/such/libman"))
    }
}
