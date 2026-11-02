package com.aatorque.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTwoDecimalPlacesTest {
    @Test
    fun keepsTrailingZero() {
        assertEquals("12.10", formatTwoDecimalPlaces(12.1f))
    }

    @Test
    fun keepsTwoZeros() {
        assertEquals("12.00", formatTwoDecimalPlaces(12.0f))
    }
}
