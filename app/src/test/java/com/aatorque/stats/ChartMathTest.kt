package com.aatorque.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartMathTest {

    private val second = 1_000.0
    private val windowMs = 60_000L

    @Test
    fun `window follows the newest reading`() {
        val window = ChartMath.follow(now = 300 * second, windowMs = windowMs)
        assertEquals(240 * second, window.min, 0.5)
        assertEquals(300 * second, window.max, 0.5)
        assertEquals(60 * second, window.width, 0.5)
        assertTrue(ChartMath.atLive(window, newest = 300 * second))
    }

    @Test
    fun `the scale does not change as a young session grows`() {
        // The reported defect: the window took its width from the readings, so 5 s of data was
        // stretched across the pane and compressed as more arrived. The width is fixed now, and
        // the empty part sits on the left.
        var window = ChartMath.follow(now = 5 * second, windowMs = windowMs)
        assertEquals(60 * second, window.width, 0.5)
        assertEquals(-55 * second, window.min, 0.5)
        window = ChartMath.follow(now = 30 * second, windowMs = windowMs)
        assertEquals(60 * second, window.width, 0.5)
        window = ChartMath.follow(now = 600 * second, windowMs = windowMs)
        assertEquals(60 * second, window.width, 0.5)
    }

    @Test
    fun `a scrolled window stays where it was put`() {
        val live = ChartMath.follow(now = 300 * second, windowMs = windowMs)
        val back = ChartMath.scroll(live, direction = -1, oldest = 0.0, newest = 300 * second)
        assertEquals(240 * second - 48 * second, back.min, 0.5)
        assertEquals(60 * second, back.width, 0.5)
        assertFalse(ChartMath.atLive(back, newest = 300 * second))
    }

    @Test
    fun `scrolling back stops at the oldest reading`() {
        var window = ChartMath.follow(now = 300 * second, windowMs = windowMs)
        repeat(10) {
            window = ChartMath.scroll(window, -1, oldest = 0.0, newest = 300 * second)
        }
        assertEquals(0.0, window.min, 0.5)
        assertEquals(60 * second, window.width, 0.5)
    }

    @Test
    fun `scrolling forward returns to live`() {
        var window = ChartMath.follow(now = 300 * second, windowMs = windowMs)
        window = ChartMath.scroll(window, -1, oldest = 0.0, newest = 300 * second)
        window = ChartMath.scroll(window, 1, oldest = 0.0, newest = 300 * second)
        assertEquals(240 * second, window.min, 0.5)
        assertEquals(300 * second, window.max, 0.5)
        assertTrue(ChartMath.atLive(window, newest = 300 * second))
    }

    @Test
    fun `a parked window returns to live when its readings are evicted`() {
        // Readings are kept for about six minutes. A window older than the oldest reading has
        // nothing left under it, so the chart goes back to live instead of drawing empty grid.
        val parked = ChartWindow(100 * second, 160 * second)
        assertFalse(ChartMath.fellOffTheEnd(parked, oldest = 60 * second))
        assertTrue(ChartMath.fellOffTheEnd(parked, oldest = 160 * second))
        assertTrue(ChartMath.fellOffTheEnd(parked, oldest = 200 * second))
    }

    @Test
    fun `clamp pulls a dragged window inside the readings`() {
        val dragged = ChartWindow(150 * second, 400 * second)
        val clamped = ChartMath.clamp(dragged, oldest = 0.0, newest = 300 * second)
        assertEquals(250 * second, clamped.width, 0.5)
        assertEquals(50 * second, clamped.min, 0.5)
        assertEquals(300 * second, clamped.max, 0.5)
    }

    @Test
    fun `clamp narrows a window wider than the readings`() {
        val tooWide = ChartWindow(-120 * second, 480 * second)
        val clamped = ChartMath.clamp(tooWide, oldest = 0.0, newest = 300 * second)
        assertEquals(300 * second, clamped.width, 0.5)
        assertEquals(0.0, clamped.min, 0.5)
    }

    @Test
    fun `a window ending at the newest reading still follows after one tick`() {
        // A tick reads the window a few seconds after it was placed now that it runs every 5 s.
        val window = ChartWindow(240 * second, 300 * second)
        assertTrue(ChartMath.atLive(window, newest = 300 * second + 1_000))
    }
}
