package com.github.iamr8.libman

import com.github.iamr8.libman.model.PendingChange.Remove
import com.github.iamr8.libman.model.PendingChange.Update
import com.github.iamr8.libman.model.PendingChanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingChangesTest {

    @Test fun `adds in click order`() {
        val q = PendingChanges.add(PendingChanges.add(emptyList(), Update("jquery", "3.7.1")), Remove("lodash"))
        assertEquals(listOf(Update("jquery", "3.7.1"), Remove("lodash")), q)
    }

    @Test fun `last click for a library wins and keeps its place`() {
        var q = PendingChanges.add(emptyList(), Update("jquery", "3.7.1"))
        q = PendingChanges.add(q, Remove("lodash"))
        q = PendingChanges.add(q, Update("jquery", "4.0.0"))
        assertEquals(listOf(Update("jquery", "4.0.0"), Remove("lodash")), q)
        q = PendingChanges.add(q, Remove("jquery"))
        assertEquals(listOf(Remove("jquery"), Remove("lodash")), q)
    }

    @Test fun `cancel and find`() {
        val q = listOf(Update("jquery", "3.7.1"), Remove("lodash"))
        assertEquals(Remove("lodash"), PendingChanges.find(q, "lodash"))
        assertNull(PendingChanges.find(q, "vue"))
        assertEquals(listOf(Remove("lodash")), PendingChanges.cancel(q, "jquery"))
        assertEquals(q, PendingChanges.cancel(q, "vue"))
    }

    @Test fun `library names are case sensitive`() {
        val q = PendingChanges.add(listOf(Remove("jquery")), Remove("jQuery"))
        assertEquals(2, q.size)
    }

    @Test fun `requeue puts not-run changes first, newer ones win`() {
        val notRun = listOf(Update("jquery", "3.7.1"), Remove("lodash"))
        val queuedSince = listOf(Update("vue", "3.5.0"), Update("lodash", "4.17.21"))
        assertEquals(
            listOf(Update("jquery", "3.7.1"), Update("vue", "3.5.0"), Update("lodash", "4.17.21")),
            PendingChanges.requeue(queuedSince, notRun),
        )
    }

    @Test fun labels() {
        assertEquals("update to 3.7.1", PendingChanges.label(Update("jquery", "3.7.1")))
        assertEquals("remove", PendingChanges.label(Remove("jquery")))
    }
}
