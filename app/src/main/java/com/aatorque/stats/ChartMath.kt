package com.aatorque.stats

/**
 * The visible x window of the chart, in epoch milliseconds.
 *
 * [min] is the left edge, [max] the right edge, and [width] how much time is on screen.
 */
data class ChartWindow(val min: Double, val max: Double) {
    val width: Double get() = max - min
}

/**
 * Window maths for the chart, kept free of Android so the follow, scroll and clamp rules can be
 * tested without a device.
 *
 * The scale never changes: the pane always shows the same amount of history, so a slope means
 * the same thing at second five as at minute ten. A session younger than the window therefore
 * shows empty grid on the left, which is the price of an honest scale.
 *
 * The window follows the newest reading until the user drags back. After that it stays where it
 * was put until the live button is pressed, or until the readings under it are evicted.
 */
object ChartMath {

    /** Reading the newest edge is "at the end" within this margin; a tick lands slightly behind. */
    const val LIVE_TOLERANCE_MS = 1_500.0

    /** One scroll step, as a fraction of the visible width. */
    private const val SCROLL_STEP = 0.8

    /** The window that ends at [now]. The width is fixed, so the scale never changes. */
    fun follow(now: Double, windowMs: Long): ChartWindow = ChartWindow(now - windowMs, now)

    /** True when the window still ends at the newest reading, so new readings belong in it. */
    fun atLive(window: ChartWindow, newest: Double): Boolean =
        window.max >= newest - LIVE_TOLERANCE_MS

    /**
     * True when every reading the window covered has been evicted, so there is nothing left to
     * look at and the chart belongs back at live instead of showing empty grid.
     */
    fun fellOffTheEnd(window: ChartWindow, oldest: Double): Boolean = window.max <= oldest

    /**
     * Walk the window through the readings. The width is kept, so the reading density does not
     * change while scrolling, and the window stops at the oldest and the newest reading.
     */
    fun scroll(window: ChartWindow, direction: Int, oldest: Double, newest: Double): ChartWindow {
        val width = minOf(window.width, (newest - oldest).coerceAtLeast(0.0))
        val step = width * SCROLL_STEP * direction
        var min = window.min + step
        if (min + width > newest) min = newest - width
        if (min < oldest) min = oldest
        return ChartWindow(min, min + width)
    }

    /**
     * Pull a window the user dragged back inside the readings. A window wider than the readings
     * is narrowed to them, so a drag cannot leave empty grid on the right.
     */
    fun clamp(window: ChartWindow, oldest: Double, newest: Double): ChartWindow {
        val allowed = (newest - oldest).coerceAtLeast(0.0)
        val width = minOf(window.width, allowed)
        var min = window.min
        if (min < oldest) min = oldest
        if (min + width > newest) min = newest - width
        if (min < oldest) min = oldest
        return ChartWindow(min, min + width)
    }
}
