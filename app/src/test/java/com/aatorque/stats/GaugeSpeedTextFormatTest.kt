package com.aatorque.stats

import com.aatorque.datastore.DecimalPlaces
import com.aatorque.datastore.Display
import org.junit.Assert.assertEquals
import org.junit.Test

class GaugeSpeedTextFormatTest {
    @Test
    fun zeroMapsToInteger() {
        val display = Display.newBuilder().setDecimalPlaces(DecimalPlaces.ZERO).build()
        assertEquals(SpeedTextFormat.INTEGER, speedTextFormat(resolvedDecimalPlaces(display)))
    }

    @Test
    fun oneMapsToDecimal() {
        val display = Display.newBuilder().setDecimalPlaces(DecimalPlaces.ONE).build()
        assertEquals(SpeedTextFormat.DECIMAL, speedTextFormat(resolvedDecimalPlaces(display)))
    }

    @Test
    fun unsetDefaultsToOneDecimal() {
        val display = Display.newBuilder().setWholeNumbers(false).build()
        assertEquals(SpeedTextFormat.DECIMAL, speedTextFormat(resolvedDecimalPlaces(display)))
    }
}
