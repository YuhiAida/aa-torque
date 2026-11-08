package com.aatorque.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * One dot per dashboard, the current one filled. Drawn rather than built from child views,
 * so ten dashboards cost ten circles and no layout pass.
 *
 * The dashboard count is capped at 10 by `screensCountOrNull`, which keeps the row short
 * enough to sit under the title on any head unit.
 */
class DashboardDots(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    var count = 0
        set(value) {
            field = value
            invalidate()
        }

    var selected = 0
        set(value) {
            field = value
            invalidate()
        }

    private val radius = 4f * resources.displayMetrics.density
    private val gap = 8f * resources.displayMetrics.density
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val idlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (count < 2) return
        val step = radius * 2 + gap
        val rowWidth = count * radius * 2 + (count - 1) * gap
        var x = (width - rowWidth) / 2f + radius
        val y = height / 2f
        for (i in 0 until count) {
            canvas.drawCircle(x, y, radius, if (i == selected) activePaint else idlePaint)
            x += step
        }
    }
}
