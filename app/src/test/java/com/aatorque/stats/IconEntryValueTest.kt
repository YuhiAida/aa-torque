package com.aatorque.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class IconEntryValueTest {
    @Test
    fun `maps a full drawable path to its bare name`() {
        assertEquals("ic_cylinder", iconEntryValue("res/drawable/ic_cylinder.png"))
    }

    @Test
    fun `leaves a bare name unchanged`() {
        assertEquals("ic_barometer", iconEntryValue("ic_barometer"))
    }

    @Test
    fun `leaves an empty value unchanged`() {
        assertEquals("", iconEntryValue(""))
    }

    @Test
    fun `handles null`() {
        assertEquals("", iconEntryValue(null))
    }
}
