package com.aatorque.stats

import com.aatorque.datastore.DecimalPlaces
import com.aatorque.datastore.Display
import org.junit.Assert.assertEquals
import org.junit.Test

class DecimalPlacesTest {
    @Test
    fun unsetWholeNumbersTrueResolvesToZero() {
        val display = Display.newBuilder().setWholeNumbers(true).build()
        assertEquals(0, TorqueData.resolvedDecimalPlaces(display))
    }

    @Test
    fun unsetWholeNumbersFalseResolvesToNull() {
        val display = Display.newBuilder().setWholeNumbers(false).build()
        assertEquals(null, TorqueData.resolvedDecimalPlaces(display))
    }

    @Test
    fun explicitZero() {
        val display = Display.newBuilder()
            .setWholeNumbers(false)
            .setDecimalPlaces(DecimalPlaces.ZERO)
            .build()
        assertEquals(0, TorqueData.resolvedDecimalPlaces(display))
    }

    @Test
    fun explicitOne() {
        val display = Display.newBuilder()
            .setWholeNumbers(true)
            .setDecimalPlaces(DecimalPlaces.ONE)
            .build()
        assertEquals(1, TorqueData.resolvedDecimalPlaces(display))
    }

    @Test
    fun explicitTwo() {
        val display = Display.newBuilder()
            .setWholeNumbers(false)
            .setDecimalPlaces(DecimalPlaces.TWO)
            .build()
        assertEquals(2, TorqueData.resolvedDecimalPlaces(display))
    }
}
