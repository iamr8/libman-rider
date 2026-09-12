package com.github.iamr8.libman

import com.github.iamr8.libman.model.SemVer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {

    private fun v(s: String) = SemVer.parse(s)!!

    @Test fun `parses core`() {
        val s = v("3.7.1")
        assertEquals(3, s.major); assertEquals(7, s.minor); assertEquals(1, s.patch)
        assertFalse(s.isPrerelease)
    }

    @Test fun `missing components default to zero`() {
        assertEquals(v("3.0.0"), v("3"))
        assertEquals(v("3.7.0"), v("3.7"))
    }

    @Test fun `parses prerelease and build`() {
        val s = v("4.0.0-beta.1+build9")
        assertEquals(4, s.major)
        assertTrue(s.isPrerelease)
        assertEquals(listOf("beta", "1"), s.prerelease)
    }

    @Test fun `non-numeric major is unparseable`() {
        assertNull(SemVer.parse("latest"))
        assertNull(SemVer.parse(""))
        assertNull(SemVer.parse(null))
    }

    @Test fun `ordering by core`() {
        assertTrue(v("4.0.0") > v("3.9.9"))
        assertTrue(v("3.8.0") > v("3.7.9"))
        assertTrue(v("3.7.2") > v("3.7.1"))
        assertEquals(0, v("3.7.1").compareTo(v("3.7.1")))
    }

    @Test fun `release outranks its prerelease`() {
        assertTrue(v("4.0.0") > v("4.0.0-rc.1"))
        assertTrue(v("4.0.0-rc.1") < v("4.0.0"))
    }

    @Test fun `prerelease identifiers ordered per spec`() {
        assertTrue(v("4.0.0-beta.2") > v("4.0.0-beta.1"))
        assertTrue(v("4.0.0-beta.11") > v("4.0.0-beta.2")) // numeric, not lexical
        assertTrue(v("4.0.0-rc.1") > v("4.0.0-beta.1"))    // alphanumeric compare
        assertTrue(v("4.0.0-alpha.1") < v("4.0.0-alpha.1.1")) // more fields is higher
    }

    @Test fun `build metadata ignored in ordering`() {
        assertEquals(0, v("3.7.1+a").compareTo(v("3.7.1+b")))
    }

    @Test fun `max of a list`() {
        val list = listOf("3.7.1", "3.7.2", "3.8.0", "4.0.0", "4.0.0-rc.2").map { v(it) }
        assertEquals(v("4.0.0"), list.max())
    }
}
