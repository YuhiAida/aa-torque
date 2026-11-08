package com.aatorque.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The chart seeds itself from this record, so the order and the eviction rule matter:
 * the series must be oldest-first and must never grow past the capacity. See issue #193.
 */
class PidHistoryTest {

    @Test
    fun `keeps readings in the order they arrived`() {
        val history = PidHistory(4)
        history.add(100, 1.0)
        history.add(200, 2.0)
        history.add(300, 3.0)

        assertEquals(3, history.size)
        assertEquals(100L, history.oldestTime ?: 0L)
        assertEquals(listOf(1.0, 2.0, 3.0), (0 until history.size).map { history.valueAt(it) })
    }

    @Test
    fun `drops the oldest reading when it is full`() {
        val history = PidHistory(3)
        listOf(1.0, 2.0, 3.0, 4.0).forEachIndexed { i, v -> history.add((i + 1) * 100L, v) }

        assertEquals(3, history.size)
        assertEquals(200L, history.oldestTime ?: 0L)
        assertEquals(listOf(2.0, 3.0, 4.0), (0 until history.size).map { history.valueAt(it) })
    }

    @Test
    fun `keeps the newest reading after wrapping twice`() {
        val history = PidHistory(2)
        listOf(1.0, 2.0, 3.0, 4.0, 5.0).forEachIndexed { i, v -> history.add(i.toLong(), v) }

        assertEquals(2, history.size)
        assertEquals(listOf(4.0, 5.0), (0 until history.size).map { history.valueAt(it) })
        assertEquals(3L, history.timeAt(0))
    }

    @Test
    fun `an empty record has no oldest time`() {
        val history = PidHistory(2)

        assertEquals(0, history.size)
        assertNull(history.oldestTime)
    }

    @Test
    fun `clear empties the record`() {
        val history = PidHistory(2)
        history.add(1, 1.0)

        history.clear()

        assertEquals(0, history.size)
        assertNull(history.oldestTime)
    }
}
