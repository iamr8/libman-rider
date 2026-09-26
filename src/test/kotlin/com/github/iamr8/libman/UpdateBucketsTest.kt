package com.github.iamr8.libman

import com.github.iamr8.libman.model.SeverityColor
import com.github.iamr8.libman.model.UpdateBuckets
import com.github.iamr8.libman.model.UpdateKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateBucketsTest {

    // The example from the request: current 3.7.1 with 3.7.2 / 3.8.0 / 4.0.0 available.
    private val available = listOf("3.6.0", "3.7.0", "3.7.1", "3.7.2", "3.8.0", "3.9.1", "4.0.0", "4.0.0-rc.2")

    @Test fun `picks highest per bucket`() {
        val b = UpdateBuckets.compute("3.7.1", available, includePrerelease = false)
        assertEquals("3.7.2", b.patch?.raw)
        assertEquals("3.9.1", b.minor?.raw) // highest minor within major 3, not just 3.8.0
        assertEquals("4.0.0", b.major?.raw)
        assertNull(b.prerelease)
    }

    @Test fun `candidates ordered patch minor major`() {
        val b = UpdateBuckets.compute("3.7.1", available, includePrerelease = false)
        val kinds = b.candidates().map { it.kind }
        assertEquals(listOf(UpdateKind.PATCH, UpdateKind.MINOR, UpdateKind.MAJOR), kinds)
    }

    @Test fun `prerelease hidden when not above the newest stable`() {
        // 4.0.0 stable exists and 4.0.0-rc.2 is lower -> only the stable chips.
        val b = UpdateBuckets.compute("3.7.1", available, includePrerelease = true)
        assertEquals("4.0.0", b.major?.raw)
        assertNull(b.prerelease)
        assertEquals(listOf(UpdateKind.PATCH, UpdateKind.MINOR, UpdateKind.MAJOR), b.candidates().map { it.kind })
    }

    @Test fun `older prerelease line is hidden when a newer major is stable`() {
        // Chart.js: on 3.9.1, with 4.5.1 stable and an old 4.0.0-release pre-release.
        val b = UpdateBuckets.compute("3.9.1", listOf("3.9.1", "4.0.0-release", "4.5.1"), includePrerelease = true)
        assertEquals("4.5.1", b.major?.raw)
        assertNull(b.prerelease)
    }

    @Test fun `prerelease below the stable update of the same core is hidden`() {
        val b = UpdateBuckets.compute("3.7.1", listOf("3.7.2-rc.1", "3.7.2"), includePrerelease = true)
        assertEquals("3.7.2", b.patch?.raw)
        assertNull(b.prerelease)
    }

    @Test fun `prerelease shown next to stable when above the newest stable`() {
        val b = UpdateBuckets.compute("3.7.1", listOf("3.7.2", "4.0.0-beta.1"), includePrerelease = true)
        assertEquals("3.7.2", b.patch?.raw)
        assertEquals("4.0.0-beta.1", b.prerelease?.raw)
        assertEquals(listOf(UpdateKind.PATCH, UpdateKind.PRERELEASE), b.candidates().map { it.kind })
    }

    @Test fun `prerelease offered when no stable update exists`() {
        val list = available + "4.1.0-beta.1"
        val b = UpdateBuckets.compute("4.0.0", list, includePrerelease = true)
        assertEquals("4.1.0-beta.1", b.prerelease?.raw)
        assertNull(b.patch); assertNull(b.minor); assertNull(b.major)
    }

    @Test fun `highest newer prerelease wins`() {
        val list = listOf("4.0.0-alpha.1", "4.0.0-rc.1", "4.0.0-beta.3")
        val b = UpdateBuckets.compute("3.7.1", list, includePrerelease = true)
        assertEquals("4.0.0-rc.1", b.prerelease?.raw)
    }

    @Test fun `prerelease not newer than current is not offered`() {
        val list = listOf("3.7.1-rc.1", "3.7.0-beta.1", "3.7.2")
        val b = UpdateBuckets.compute("3.7.1", list, includePrerelease = true)
        assertNull(b.prerelease)
        assertEquals("3.7.2", b.patch?.raw)
    }

    @Test fun `prerelease excluded when includePrerelease false`() {
        val list = listOf("4.0.0", "4.1.0-beta.1")
        val b = UpdateBuckets.compute("4.0.0", list, includePrerelease = true)
        assertEquals("4.1.0-beta.1", b.prerelease?.raw)
        val b2 = UpdateBuckets.compute("4.0.0", list, includePrerelease = false)
        assertNull(b2.prerelease)
    }

    @Test fun `no updates when already latest`() {
        val b = UpdateBuckets.compute("4.0.0", available, includePrerelease = false)
        assertFalse(b.hasAny())
        assertEquals(SeverityColor.NONE, b.highestColor())
    }

    @Test fun `highest color reflects biggest jump`() {
        assertEquals(SeverityColor.RED, UpdateBuckets.compute("3.7.1", available, false).highestColor())
        assertEquals(SeverityColor.YELLOW, UpdateBuckets.compute("3.7.1", listOf("3.7.2", "3.8.0"), false).highestColor())
        assertEquals(SeverityColor.GREEN, UpdateBuckets.compute("3.7.1", listOf("3.7.2"), false).highestColor())
    }

    @Test fun `unparseable current yields no updates`() {
        val b = UpdateBuckets.compute("latest", available, includePrerelease = true)
        assertFalse(b.hasAny())
    }

    @Test fun `kind colors follow legend`() {
        assertEquals(SeverityColor.GREEN, UpdateKind.PATCH.color)
        assertEquals(SeverityColor.YELLOW, UpdateKind.MINOR.color)
        assertEquals(SeverityColor.RED, UpdateKind.MAJOR.color)
        assertEquals(SeverityColor.RED, UpdateKind.PRERELEASE.color)
    }
}
