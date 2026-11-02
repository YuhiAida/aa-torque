package com.aatorque.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreensCountValidationTest {
    @Test
    fun emptyReturnsNull() = assertEquals(null, screensCountOrNull(""))

    @Test
    fun nonNumericReturnsNull() = assertEquals(null, screensCountOrNull("abc"))

    @Test
    fun zeroReturnsNull() = assertEquals(null, screensCountOrNull("0"))

    @Test
    fun oneIsValid() = assertEquals(1, screensCountOrNull("1"))

    @Test
    fun tenIsValid() = assertEquals(10, screensCountOrNull("10"))

    @Test
    fun elevenReturnsNull() = assertEquals(null, screensCountOrNull("11"))
}
