package com.github.iamr8.libman

import com.github.iamr8.libman.model.SeverityColor
import com.github.iamr8.libman.model.UpdateKind
import com.github.iamr8.libman.model.computeUpdateKind
import com.github.iamr8.libman.model.isPrerelease
import com.github.iamr8.libman.model.severityColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeverityTest {

    @Test fun `major bump`() {
        assertEquals(UpdateKind.MAJOR, computeUpdateKind("3.6.0", "4.0.0"))
        assertEquals(SeverityColor.RED, severityColor("3.6.0", "4.0.0"))
    }

    @Test fun `minor bump`() {
        assertEquals(UpdateKind.MINOR, computeUpdateKind("3.6.0", "3.7.0"))
        assertEquals(SeverityColor.YELLOW, severityColor("3.6.0", "3.7.0"))
    }

    @Test fun `patch bump`() {
        assertEquals(UpdateKind.PATCH, computeUpdateKind("3.6.0", "3.6.1"))
        assertEquals(SeverityColor.GREEN, severityColor("3.6.0", "3.6.1"))
    }

    @Test fun `no change`() {
        assertEquals(UpdateKind.NONE, computeUpdateKind("3.6.0", "3.6.0"))
        assertEquals(SeverityColor.NONE, severityColor("3.6.0", "3.6.0"))
    }

    @Test fun `prerelease latest is always red`() {
        assertEquals(UpdateKind.PRERELEASE, computeUpdateKind("3.6.0", "4.0.0-beta.1"))
        assertEquals(SeverityColor.RED, severityColor("3.6.0", "4.0.0-beta.1"))
    }

    @Test fun `unparseable is unknown-red`() {
        assertEquals(UpdateKind.UNKNOWN, computeUpdateKind("latest", "4.0.0"))
        assertEquals(SeverityColor.RED, severityColor("latest", "4.0.0"))
    }

    @Test fun `missing components default to zero`() {
        assertEquals(UpdateKind.MINOR, computeUpdateKind("3", "3.1"))
        assertEquals(UpdateKind.PATCH, computeUpdateKind("3.6", "3.6.2"))
        assertEquals(UpdateKind.NONE, computeUpdateKind("3.0.0", "3"))
    }

    @Test fun `build metadata ignored`() {
        assertEquals(UpdateKind.PATCH, computeUpdateKind("3.6.0+build1", "3.6.1+build2"))
    }

    @Test fun `isPrerelease detection`() {
        assertTrue(isPrerelease("4.0.0-beta.1"))
        assertFalse(isPrerelease("4.0.0"))
        assertFalse(isPrerelease("4.0.0+meta"))
        assertFalse(isPrerelease(null))
    }
}
