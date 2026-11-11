package com.aatorque.stats

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import com.jjoe64.graphview.GraphView
import kotlin.math.abs

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

    /**
     * Called when the gesture that started here is mostly vertical, with -1 for down and 1 for
     * up. The graph consumes every touch that begins on it, so the dashboard's own detector
     * never sees that swipe; the owner pages screens from this. A mostly horizontal drag stays
     * with the viewport, which is the chart's own scroll.
     */
    var onVerticalFling: ((Int) -> Unit)? = null

    private val flingDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (abs(diffY) <= abs(diffX) || abs(diffY) <= 100f) return false
                onVerticalFling?.invoke(if (diffY > 0) -1 else 1)
                return true
            }
        }
    )

    /** True while the current gesture started on the graph, so the owner must not act on it. */
    var handledGesture = false
        private set

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Use the masked action: a second finger moves the pointer index into the low bits,
        // and a missed ACTION_UP would leave handledGesture true for the next swipe.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            handledGesture = true
        }
        flingDetector.onTouchEvent(event)
        val handled = super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
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
