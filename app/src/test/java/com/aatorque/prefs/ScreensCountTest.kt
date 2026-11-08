package com.aatorque.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The dashboard count is typed into a dialog, so the range is checked here. Ten is the
 * ceiling: the car screen draws one dot per dashboard, and a longer row stops fitting under
 * the title. See issue #159.
 */
class ScreensCountTest {

    @Test
    fun `accepts one dashboard`() {
        assertEquals(1, screensCountOrNull("1"))
    }

    @Test
    fun `accepts the maximum of ten`() {
        assertEquals(10, screensCountOrNull("10"))
    }

    @Test
    fun `rejects eleven dashboards`() {
        assertNull(screensCountOrNull("11"))
    }

    @Test
    fun `rejects zero and negative counts`() {
        assertNull(screensCountOrNull("0"))
        assertNull(screensCountOrNull("-1"))
    }

    @Test
    fun `rejects text and empty input`() {
        assertNull(screensCountOrNull("two"))
        assertNull(screensCountOrNull(""))
        assertNull(screensCountOrNull(null))
    }
}
