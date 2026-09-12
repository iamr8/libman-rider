package com.github.iamr8.libman

import com.github.iamr8.libman.model.LibraryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryIdTest {

    @Test fun `plain name with version`() {
        val id = LibraryId.parse("jquery@3.6.0", "cdnjs")
        assertEquals("jquery", id.name)
        assertEquals("3.6.0", id.version)
        assertFalse(id.isVersionless)
    }

    @Test fun `plain name without version`() {
        val id = LibraryId.parse("jquery", "cdnjs")
        assertEquals("jquery", id.name)
        assertNull(id.version)
        assertTrue(id.isVersionless)
    }

    @Test fun `scoped npm name with version`() {
        val id = LibraryId.parse("@microsoft/signalr@8.0.0", "unpkg")
        assertEquals("@microsoft/signalr", id.name)
        assertEquals("8.0.0", id.version)
    }

    @Test fun `scoped npm name with latest tag`() {
        val id = LibraryId.parse("@microsoft/signalr@latest", "unpkg")
        assertEquals("@microsoft/signalr", id.name)
        assertEquals("latest", id.version)
    }

    @Test fun `scoped npm name without version`() {
        val id = LibraryId.parse("@popperjs/core", "unpkg")
        assertEquals("@popperjs/core", id.name)
        assertNull(id.version)
    }

    @Test fun `jsdelivr github form with version`() {
        val id = LibraryId.parse("jquery/jquery@3.6.0", "jsdelivr")
        assertEquals("jquery/jquery", id.name)
        assertEquals("3.6.0", id.version)
    }

    @Test fun `filesystem path is never versioned`() {
        val id = LibraryId.parse("C:\\temp\\contosoCalendar\\", "filesystem")
        assertEquals("C:\\temp\\contosoCalendar\\", id.name)
        assertNull(id.version)
        assertTrue(id.isVersionless)
    }

    @Test fun `filesystem provider is case-insensitive`() {
        val id = LibraryId.parse("../lib/foo@bar", "FileSystem")
        assertEquals("../lib/foo@bar", id.name)
        assertNull(id.version)
    }

    @Test fun `trailing at with no version yields null version`() {
        val id = LibraryId.parse("jquery@", "cdnjs")
        assertEquals("jquery", id.name)
        assertNull(id.version)
    }

    @Test fun `surrounding whitespace is trimmed`() {
        val id = LibraryId.parse("  jquery@3.6.0  ", "cdnjs")
        assertEquals("jquery", id.name)
        assertEquals("3.6.0", id.version)
    }
}
