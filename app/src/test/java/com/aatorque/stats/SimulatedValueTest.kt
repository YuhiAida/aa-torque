package com.aatorque.stats

import com.aatorque.datastore.Display
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatedValueTest {
    private val period = 20_000L

    private fun display(min: Int, max: Int, pid: String = "torque_0d,0", whole: Boolean = false) =
        Display.newBuilder()
            .setPid(pid)
            .setMinValue(min)
            .setMaxValue(max)
            .setWholeNumbers(whole)
            .build()

    /** First instant of the cycle for this display, so the expected points are exact. */
    private fun start(display: Display) = period - SimulatedValue.offsetMs(display.pid)

    @Test
    fun sweepsFromMinimumToMaximumAndBack() {
        val display = display(0, 100, pid = "sweep")
        val first = start(display)

        assertEquals(0.0, SimulatedValue.at(display, first, period), 0.001)
        assertEquals(50.0, SimulatedValue.at(display, first + period / 4, period), 0.001)
        assertEquals(100.0, SimulatedValue.at(display, first + period / 2, period), 0.001)
        assertEquals(50.0, SimulatedValue.at(display, first + period * 3 / 4, period), 0.001)
        assertEquals(0.0, SimulatedValue.at(display, first + period, period), 0.001)
    }

    @Test
    fun negativeRangeSweepsBetweenTheConfiguredEnds() {
        val display = display(-20, 40, pid = "temperature")
        val first = start(display)

        assertEquals(-20.0, SimulatedValue.at(display, first, period), 0.001)
        assertEquals(40.0, SimulatedValue.at(display, first + period / 2, period), 0.001)
    }

    @Test
    fun reversedEndsAreOrdered() {
        val display = display(90, 10, pid = "reversed")
        val first = start(display)

        assertEquals(10.0, SimulatedValue.at(display, first, period), 0.001)
        assertEquals(90.0, SimulatedValue.at(display, first + period / 2, period), 0.001)
    }

    @Test
    fun wholeNumbersStayWhole() {
        val display = display(0, 5, pid = "whole", whole = true)

        assertEquals(
            1.0,
            SimulatedValue.at(display, start(display) + period / 8, period),
            0.0
        )
    }

    @Test
    fun unsetRangeStillMoves() {
        val display = display(0, 0, pid = "unset")
        val first = start(display)

        assertEquals(0.0, SimulatedValue.at(display, first, period), 0.001)
        assertEquals(100.0, SimulatedValue.at(display, first + period / 2, period), 0.001)
    }

    @Test
    fun equalNonZeroEndsHoldTheValue() {
        val display = display(50, 50, pid = "fixed")

        assertEquals(50.0, SimulatedValue.at(display, 0, period), 0.001)
    }

    @Test
    fun differentPidsDoNotSweepInStep() {
        assertTrue(
            SimulatedValue.offsetMs("torque_0c,0") != SimulatedValue.offsetMs("torque_0d,0")
        )
    }
}
