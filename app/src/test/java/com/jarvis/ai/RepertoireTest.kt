package com.jarvis.ai

import com.jarvis.ai.core.agent.Repertoire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepertoireTest {

    @Test
    fun `every pool has variety`() {
        listOf(
            Repertoire.busy,
            Repertoire.faults,
            Repertoire.greetingsDormant,
            Repertoire.identityDormant,
            Repertoire.thanks,
            Repertoire.coreOffline,
            Repertoire.unmapped,
            Repertoire.emptyThought
        ).forEach { pool ->
            assertTrue("pool too small: $pool", pool.size >= 2)
        }
    }

    @Test
    fun `selection always lands inside the pool`() {
        repeat(500) { seed ->
            val line = Repertoire.from(Repertoire.busy, seed.toLong())
            assertTrue(line in Repertoire.busy)
        }
    }

    @Test
    fun `selection is deterministic for a fixed seed`() {
        val a = Repertoire.from(Repertoire.faults, seed = 42L)
        val b = Repertoire.from(Repertoire.faults, seed = 42L)
        assertEquals(a, b)
    }
}
