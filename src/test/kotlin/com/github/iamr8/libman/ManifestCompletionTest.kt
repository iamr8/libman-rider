package com.github.iamr8.libman

import com.github.iamr8.libman.model.DestinationDirs
import com.github.iamr8.libman.model.LibraryInput
import com.github.iamr8.libman.model.ManifestFiles
import com.github.iamr8.libman.model.VersionSuggestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestCompletionTest {

    @Test fun `library input - name or version`() {
        assertEquals(LibraryInput.Name("jq"), LibraryInput.parse("jq"))
        assertEquals(LibraryInput.Name(""), LibraryInput.parse(""))
        assertEquals(LibraryInput.Version("jquery", ""), LibraryInput.parse("jquery@"))
        assertEquals(LibraryInput.Version("jquery", "3."), LibraryInput.parse("jquery@3."))
    }

    @Test fun `library input - scoped names`() {
        assertEquals(LibraryInput.Name("@"), LibraryInput.parse("@"))
        assertEquals(LibraryInput.Name("@types/no"), LibraryInput.parse("@types/no"))
        assertEquals(LibraryInput.Version("@types/node", "2"), LibraryInput.parse("@types/node@2"))
    }

    @Test fun `versions newest first, limited`() {
        val all = listOf("3.6.0", "4.0.0", "3.7.1", "4.0.0-rc.1", "latest", "3.7.1")
        assertEquals(listOf("4.0.0", "3.7.1", "3.6.0"), VersionSuggestions.latest(all, includePrerelease = false))
        assertEquals(listOf("4.0.0", "4.0.0-rc.1"), VersionSuggestions.latest(all, includePrerelease = true, limit = 2))
    }

    @Test fun `files - missing entries`() {
        val available = listOf("/dist/jquery.js", "/dist/jquery.min.js")
        assertEquals(
            listOf("dist/jquery.slim.js"),
            ManifestFiles.missing(listOf("dist/jquery.min.js", "/dist/jquery.js", "dist/jquery.slim.js", "dist/*.map", " "), available),
        )
    }

    @Test fun `files - patterns and normalize`() {
        assertTrue(ManifestFiles.isPattern("dist/*.js"))
        assertTrue(ManifestFiles.isPattern("dist/{a,b}.js"))
        assertFalse(ManifestFiles.isPattern("dist/jquery.js"))
        assertEquals("dist/a.js", ManifestFiles.normalize(" /dist/a.js "))
    }

    @Test fun `destination folders skip hidden and build output`() {
        assertTrue(DestinationDirs.skip(".git"))
        assertTrue(DestinationDirs.skip("node_modules"))
        assertTrue(DestinationDirs.skip("Bin"))
        assertFalse(DestinationDirs.skip("wwwroot"))
    }
}
