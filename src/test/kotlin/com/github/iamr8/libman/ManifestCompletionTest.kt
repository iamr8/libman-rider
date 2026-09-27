package com.github.iamr8.libman

import com.github.iamr8.libman.model.DestinationDirs
import com.github.iamr8.libman.model.LibraryId
import com.github.iamr8.libman.model.LibraryInput
import com.github.iamr8.libman.model.LibraryNameSuggestions
import com.github.iamr8.libman.model.ManifestFiles
import com.github.iamr8.libman.model.VersionSuggestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test fun `library names - search from 3 letters`() {
        assertFalse(LibraryNameSuggestions.canSearch(""))
        assertFalse(LibraryNameSuggestions.canSearch("bo"))
        assertFalse(LibraryNameSuggestions.canSearch(" bo "))
        assertTrue(LibraryNameSuggestions.canSearch("boo"))
    }

    @Test fun `library names - only those starting with the typed text`() {
        val hits = listOf("bootstrap", "angular-bootstrap", "Bootbox", "react-boo", "@types/boom")
        assertEquals(listOf("bootstrap", "Bootbox"), LibraryNameSuggestions.startingWith(hits, "boo") { it })
        assertEquals(listOf("@types/boom"), LibraryNameSuggestions.startingWith(hits, "@types/b") { it })
    }

    @Test fun `library names - chosen name writes name@version`() {
        // Another library: its latest version (the one the list shows) replaces the old one.
        assertEquals("jquery.isotope@3.0.6", LibraryNameSuggestions.chosenValue("jquery.isotope", "3.0.6", LibraryId.parse("jqu@3.7.1", null)))
        assertEquals("jquery@4.0.0", LibraryNameSuggestions.chosenValue("jquery", "4.0.0", LibraryId.parse("jqu", null)))
        assertEquals("jquery@4.0.0", LibraryNameSuggestions.chosenValue("jquery", "4.0.0", null))
        // The same library keeps its version.
        assertEquals("jquery@3.7.1", LibraryNameSuggestions.chosenValue("jquery", "4.0.0", LibraryId.parse("jquery@3.7.1", null)))
        // No version known: end in `@`, so the version list can open.
        assertEquals("bootbox@", LibraryNameSuggestions.chosenValue("bootbox", null, LibraryId.parse("boo@1.0.0", null)))
    }

    @Test fun `library names - closing quote of the value`() {
        val line = "\"library\": \"jqu@3.7.1\","
        assertEquals(line.lastIndexOf('"'), LibraryNameSuggestions.closingQuote(line, line.indexOf("jqu") + 3))
        val escaped = "\"a\\\"b\" x"
        assertEquals(5, LibraryNameSuggestions.closingQuote(escaped, 1))
        assertNull(LibraryNameSuggestions.closingQuote("\"abc\n\"", 1))
    }

    @Test fun `library names - at most 50`() {
        val hits = (1..80).map { "boo-$it" }
        assertEquals((1..50).map { "boo-$it" }, LibraryNameSuggestions.startingWith(hits, "boo") { it })
        assertEquals(listOf("boo-1", "boo-2"), LibraryNameSuggestions.startingWith(hits, "boo", limit = 2) { it })
    }

    @Test fun `versions newest first, limited`() {
        val all = listOf("3.6.0", "4.0.0", "3.7.1", "4.0.0-rc.1", "latest", "3.7.1")
        assertEquals(listOf("4.0.0", "3.7.1", "3.6.0"), VersionSuggestions.latest(all, includePrerelease = false))
        assertEquals(listOf("4.0.0", "4.0.0-rc.1"), VersionSuggestions.latest(all, includePrerelease = true, limit = 2))
    }

    @Test fun `versions - at most 10, pre-releases only when enabled`() {
        val all = (1..12).map { "1.$it.0" } + listOf("2.0.0-alpha.1", "2.0.0-beta.2", "2.0.0-rc.1")
        assertEquals(
            listOf("2.0.0-rc.1", "2.0.0-beta.2", "2.0.0-alpha.1", "1.12.0", "1.11.0", "1.10.0", "1.9.0", "1.8.0", "1.7.0", "1.6.0"),
            VersionSuggestions.latest(all, includePrerelease = true),
        )
        assertEquals(
            listOf("1.12.0", "1.11.0", "1.10.0", "1.9.0", "1.8.0", "1.7.0", "1.6.0", "1.5.0", "1.4.0", "1.3.0"),
            VersionSuggestions.latest(all, includePrerelease = false),
        )
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

    @Test fun `files - destination folder path`() {
        assertEquals("wwwroot/lib/jquery", ManifestFiles.normalizeDir(" wwwroot/lib/jquery/ "))
        assertEquals("./wwwroot/lib", ManifestFiles.normalizeDir("./wwwroot/lib/"))
    }

    @Test fun `files - relative to a file mapping root`() {
        val files = listOf("dist/jquery.js", "/dist/jquery.min.js", "src/core.js", "distx/a.js")
        assertEquals(listOf("jquery.js", "jquery.min.js"), ManifestFiles.underRoot(files, "dist"))
        assertEquals(listOf("jquery.js", "jquery.min.js"), ManifestFiles.underRoot(files, "/dist/"))
        assertEquals(listOf("dist/jquery.js", "dist/jquery.min.js", "src/core.js", "distx/a.js"), ManifestFiles.underRoot(files, null))
    }

    @Test fun `destination folders skip hidden and build output`() {
        assertTrue(DestinationDirs.skip(".git"))
        assertTrue(DestinationDirs.skip("node_modules"))
        assertTrue(DestinationDirs.skip("Bin"))
        assertFalse(DestinationDirs.skip("wwwroot"))
    }
}
