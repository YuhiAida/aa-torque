package com.aatorque.stats

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import com.jjoe64.graphview.GraphView

/**
 * GraphView that cannot be dragged outside the readings it holds.
 *
 * GraphView scrolls without bounds once the x axis is manual, so a drag to the left ran past
 * the oldest reading into empty grid and the time labels went negative. The owner sets
 * [minAllowed] and [maxAllowed] as data arrives, and every finished drag is pulled back inside
 * them. See issue #159.
 *
 * The viewport is read with `getMinX(false)` / `getMaxX(false)`: the `true` form returns the
 * complete range of the series, which is the whole session, not the visible window.
 */
class ClampedGraphView(context: Context, attrs: AttributeSet?) : GraphView(context, attrs) {

    var minAllowed = 0.0
    var maxAllowed = 0.0

    /** Called after a finished drag was clamped, so the owner can re-read the window. */
    var onViewportSettled: (() -> Unit)? = null

    /** True while the current gesture started on the graph, so the owner must not act on it. */
    var handledGesture = false
        private set

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            handledGesture = true
        }
        val handled = super.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            clamp()
            onViewportSettled?.invoke()
            handledGesture = false
        }
        return handled
    }

    /** Pull the visible window back inside [minAllowed]..[maxAllowed]. */
    fun clamp() {
        val clamped = ChartMath.clamp(
            ChartWindow(viewport.getMinX(false), viewport.getMaxX(false)),
            minAllowed,
            maxAllowed
        )
        viewport.setMinX(clamped.min)
        viewport.setMaxX(clamped.max)
    }
}
