package com.github.iamr8.libman

import com.github.iamr8.libman.util.OptionalApiCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionalApiCallTest {

    @Test fun `runs the block while the api works`() {
        var runs = 0
        val call = OptionalApiCall { error("not broken") }
        assertTrue(call.run { runs++ })
        assertTrue(call.run { runs++ })
        assertEquals(2, runs)
    }

    @Test fun `missing class reports once and skips later calls`() {
        val reported = mutableListOf<LinkageError>()
        var runs = 0
        val call = OptionalApiCall { reported += it }
        assertFalse(call.run { runs++; throw NoClassDefFoundError("gone") })
        assertFalse(call.run { runs++ })
        assertEquals(1, runs)
        assertEquals(1, reported.size)
    }

    @Test fun `missing method counts as broken`() {
        var reported = 0
        val call = OptionalApiCall { reported++ }
        assertFalse(call.run { throw NoSuchMethodError("gone") })
        assertEquals(1, reported)
    }

    @Test(expected = IllegalStateException::class)
    fun `other errors are not swallowed`() {
        OptionalApiCall { }.run { throw IllegalStateException("a real bug") }
    }
}
