package com.github.iamr8.libman

import com.github.iamr8.libman.model.ManifestDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManifestDefaultsTest {

    @Test fun `provider - own, else the manifest default`() {
        assertEquals("unpkg", ManifestDefaults.provider("unpkg", "cdnjs"))
        assertEquals("jsdelivr", ManifestDefaults.provider(null, "jsdelivr"))
        assertNull(ManifestDefaults.provider(null, null))
    }

    @Test fun `provider - only a missing value falls back, like LibMan`() {
        assertEquals("", ManifestDefaults.provider("", "unpkg"))
    }

    @Test fun `destination - own, else the manifest default`() {
        assertEquals("wwwroot/js", ManifestDefaults.destination("wwwroot/js", "lib/[Name]", "jquery", "3.7.1"))
        assertEquals("wwwroot/lib", ManifestDefaults.destination(null, "wwwroot/lib", "jquery", "3.7.1"))
        assertNull(ManifestDefaults.destination(null, null, "jquery", "3.7.1"))
    }

    @Test fun `destination - default tokens expanded`() {
        assertEquals("lib/jquery/3.7.1", ManifestDefaults.destination(null, "lib/[Name]/[Version]", "jquery", "3.7.1"))
        // [Name] is the last segment of a scoped or path name.
        assertEquals("lib/core", ManifestDefaults.destination(null, "lib/[Name]", "@popperjs/core", "2.11.8"))
        assertEquals("lib/core/", ManifestDefaults.destination(null, "lib/[Name]/[Version]", "@popperjs/core", null))
    }
}
