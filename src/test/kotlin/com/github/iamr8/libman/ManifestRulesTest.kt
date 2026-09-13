package com.github.iamr8.libman

import com.github.iamr8.libman.model.ManifestRules
import com.github.iamr8.libman.model.ManifestRules.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestRulesTest {

    @Test fun `known providers are accepted, any case`() {
        assertNull(ManifestRules.providerProblem("cdnjs"))
        assertNull(ManifestRules.providerProblem("CDNJS"))
        assertNull(ManifestRules.providerProblem(" jsdelivr "))
        assertNull(ManifestRules.providerProblem("unpkg"))
        assertNull(ManifestRules.providerProblem("filesystem"))
    }

    @Test fun `null or blank provider is fine (inherits default)`() {
        assertNull(ManifestRules.providerProblem(null))
        assertNull(ManifestRules.providerProblem(""))
        assertNull(ManifestRules.providerProblem("   "))
    }

    @Test fun `unknown provider is a problem`() {
        val msg = ManifestRules.providerProblem("bower")
        assertTrue(msg != null && msg.contains("bower"))
    }

    @Test fun `latest version has no problem`() {
        assertNull(ManifestRules.versionProblem("3.0"))
        assertNull(ManifestRules.versionProblem(" 3.0 "))
        assertNull(ManifestRules.versionProblem(null))
        assertNull(ManifestRules.versionProblem(""))
    }

    @Test fun `older known version informs about the newer schema`() {
        val p = ManifestRules.versionProblem("1.0")
        assertEquals(Level.INFO, p?.first)
        assertTrue(p!!.second.contains("3.0"))
    }

    @Test fun `unknown version is a warning`() {
        val p = ManifestRules.versionProblem("2.0")
        assertEquals(Level.WARNING, p?.first)
    }
}
