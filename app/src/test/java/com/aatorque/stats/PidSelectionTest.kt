package com.aatorque.stats

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the PID rule from docs/pid-filtering.md. If someone simplifies this back to
 * `listAllPIDs()`, these tests fail.
 */
class PidSelectionTest {

    @Test
    fun `drops a dictionary PID the ECU does not answer`() {
        val all = listOf("05,0", "0c,0", "11,0")
        val supported = setOf("0c,0")

        assertEquals(listOf("0c,0"), selectPids(all, supported))
    }

    @Test
    fun `keeps a synthetic ff metric the ECU never reports`() {
        val all = listOf("0c,0", "ff1001")
        val supported = setOf("0c,0")

        assertEquals(listOf("0c,0", "ff1001"), selectPids(all, supported))
    }

    @Test
    fun `falls back to the full dictionary when nothing is supported`() {
        val all = listOf("05,0", "0c,0", "ff1001")

        assertEquals(all, selectPids(all, emptySet()))
    }

    @Test
    fun `keeps every supported PID and nothing else`() {
        val all = listOf("05,0", "0c,0", "11,0", "ff1001")
        val supported = setOf("05,0", "11,0")

        assertEquals(listOf("05,0", "11,0", "ff1001"), selectPids(all, supported))
    }
}
