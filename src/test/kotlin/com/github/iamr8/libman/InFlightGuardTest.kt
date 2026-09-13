package com.github.iamr8.libman

import com.github.iamr8.libman.cli.InFlightGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InFlightGuardTest {

    @Test fun `second acquire of the same key is refused until released`() {
        val guard = InFlightGuard()
        assertTrue(guard.tryAcquire("k"))
        assertFalse("a running key must not be re-acquired", guard.tryAcquire("k"))
        assertTrue(guard.isActive("k"))
        guard.release("k")
        assertFalse(guard.isActive("k"))
        assertTrue("released key can be acquired again", guard.tryAcquire("k"))
    }

    @Test fun `different keys are independent`() {
        val guard = InFlightGuard()
        assertTrue(guard.tryAcquire("a"))
        assertTrue(guard.tryAcquire("b"))
        assertFalse(guard.tryAcquire("a"))
        guard.release("a")
        assertTrue(guard.isActive("b"))
        assertTrue(guard.tryAcquire("a"))
    }

    @Test fun `releasing an unheld key is harmless`() {
        val guard = InFlightGuard()
        guard.release("never-held")
        assertFalse(guard.isActive("never-held"))
    }
}
