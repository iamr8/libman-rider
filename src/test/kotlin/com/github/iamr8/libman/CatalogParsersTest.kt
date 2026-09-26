package com.github.iamr8.libman

import com.github.iamr8.libman.provider.CatalogParsers
import com.github.iamr8.libman.provider.LibrarySuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogParsersTest {

    @Test fun `cdnjs shape`() {
        val json = """{"name":"jquery","description":"JavaScript library for DOM operations","versions":["4.0.0","3.7.1","3.6.0"]}"""
        val info = CatalogParsers.parseCdnjs(json)!!
        assertEquals("JavaScript library for DOM operations", info.description)
        assertEquals(listOf("4.0.0", "3.7.1", "3.6.0"), info.versions)
    }

    @Test fun `npm shape uses version keys`() {
        val json = """{"description":"The most popular front-end framework","dist-tags":{"latest":"5.3.8"},"versions":{"5.3.8":{"x":1},"5.3.7":{}}}"""
        val info = CatalogParsers.parseNpm(json)!!
        assertEquals("The most popular front-end framework", info.description)
        assertTrue(info.versions.containsAll(listOf("5.3.8", "5.3.7")))
    }

    @Test fun `jsdelivr shape has versions but no description`() {
        val json = """{"type":"npm","tags":{"latest":"5.3.8"},"versions":[{"version":"5.3.8"},{"version":"5.3.7"}]}"""
        val info = CatalogParsers.parseJsdelivr(json)!!
        assertNull(info.description)
        assertEquals(listOf("5.3.8", "5.3.7"), info.versions)
    }

    @Test fun `blank description becomes null`() {
        val json = """{"description":"  ","versions":["1.0.0"]}"""
        assertNull(CatalogParsers.parseCdnjs(json)!!.description)
    }

    @Test fun `cdnjs search`() {
        val json = """{"results":[{"name":"jquery","latest":"https://x/jquery.min.js","version":"4.0.0","description":"DOM"},{"version":"1"}],"total":2}"""
        assertEquals(
            listOf(LibrarySuggestion("jquery", "4.0.0", "DOM")),
            CatalogParsers.parseCdnjsSearch(json),
        )
    }

    @Test fun `npm search`() {
        val json = """{"objects":[{"downloads":{},"package":{"name":"jq","version":"1.7.2","description":"jQuery wrapper"}},{"package":{"name":"@scope/x"}}]}"""
        assertEquals(
            listOf(LibrarySuggestion("jq", "1.7.2", "jQuery wrapper"), LibrarySuggestion("@scope/x", null, null)),
            CatalogParsers.parseNpmSearch(json),
        )
    }

    @Test fun `file lists`() {
        assertEquals(listOf("jquery.js", "jquery.min.js"), CatalogParsers.parseCdnjsFiles("""{"files":["jquery.js","jquery.min.js"]}"""))
        val jsd = """{"type":"npm","files":[{"name":"/AUTHORS.txt","size":1},{"name":"/dist/jquery.js"}]}"""
        assertEquals(listOf("AUTHORS.txt", "dist/jquery.js"), CatalogParsers.parseJsdelivrFiles(jsd))
        assertNull(CatalogParsers.parseCdnjsFiles("""{"error":true}"""))
        assertNull(CatalogParsers.parseNpmSearch("[]"))
    }

    @Test fun `malformed json returns null`() {
        assertNull(CatalogParsers.parseCdnjs("not json"))
        assertNull(CatalogParsers.parseNpm("[]"))
        assertNull(CatalogParsers.parseJsdelivr("""{"no":"versions"}"""))
    }
}
