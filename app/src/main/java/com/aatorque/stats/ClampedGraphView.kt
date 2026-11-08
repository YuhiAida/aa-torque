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
 */
class ClampedGraphView(context: Context, attrs: AttributeSet?) : GraphView(context, attrs) {

    var minAllowed = 0.0
    var maxAllowed = 0.0

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = super.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            clamp()
        }
        return handled
    }

    /** Pull the visible window back inside [minAllowed]..[maxAllowed]. */
    fun clamp() {
        val dataSpan = maxAllowed - minAllowed
        if (dataSpan <= 0.0) return
        val viewport = viewport
        // A zoomed-out viewport can be wider than the readings; keep the data range as the
        // ceiling so a gesture cannot leave the window showing empty grid.
        val span = (viewport.getMaxX(true) - viewport.getMinX(true)).coerceAtMost(dataSpan)
        var min = viewport.getMinX(true)
        if (min < minAllowed) min = minAllowed
        if (min + span > maxAllowed) min = maxAllowed - span
        if (min < minAllowed) min = minAllowed
        viewport.setMinX(min)
        viewport.setMaxX(min + span)
    }
}
