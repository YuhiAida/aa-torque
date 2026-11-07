package com.aatorque.stats

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the column order from Torque's AIDL contract, `ITorqueService.getPIDInformation`:
 * `<longName>,<shortName>,<unit>,<maxValue>,<minValue>,<scale>`. Index 3 is the maximum.
 * Swapping the two bound assignments makes every new gauge start inverted. See issue #157.
 */
class PidRangeTest {

    @Test
    fun `reads the maximum from column 3 and the minimum from column 4`() {
        // The example Torque gives in the AIDL: pressure 0..255 kPa.
        val row = "Intake Manifold Pressure,Intake,kPa,255,0,1".split(",")

        assertEquals(0 to 255, pidRange(row))
    }

    @Test
    fun `rounds the maximum up and the minimum down`() {
        val row = "Boost,Boost,bar,2.5,0.4,1".split(",")

        assertEquals(0 to 3, pidRange(row))
    }

    @Test
    fun `keeps a negative minimum`() {
        val row = "Timing advance,Timing,deg,63.5,-64,1".split(",")

        assertEquals(-64 to 64, pidRange(row))
    }

    @Test
    fun `falls back to 0 to 100 when both bounds are zero`() {
        val row = "Unknown,Unknown,,0,0,1".split(",")

        assertEquals(0 to 100, pidRange(row))
    }
}
