package com.aatorque.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * One dot per dashboard, the current one filled, and a chevron that points at the chart.
 *
 * Drawn rather than built from child views, so ten dashboards cost ten circles and no layout
 * pass. The dashboard count is capped at 10 by `screensCountOrNull`, which keeps the row short
 * enough to sit under the title on any head unit.
 *
 * The chevron is the only sign that a swipe down opens the chart. It points down on the
 * gauges and up once the chart is what is on screen.
 */
class DashboardDots(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    /** Tapping the row toggles the chart, which is what the chevron points at. */
    var onChartToggle: (() -> Unit)? = null

    init {
        isClickable = true
        contentDescription = context.getString(R.string.chart_toggle)
    }

    override fun performClick(): Boolean {
        onChartToggle?.invoke()
        return super.performClick()
    }

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

    /** Show the chart chevron at the end of the row. */
    var chartHint = false
        set(value) {
            field = value
            invalidate()
        }

    /** True while the chart is open, which flips the chevron. */
    var chartOpen = false
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val radius = 4f * density
    private val gap = 8f * density
    private val hintGap = 14f * density
    private val chevronWidth = 10f * density
    private val chevronHeight = 6f * density
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val idlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }
    private val chevronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val chevronPath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dots = if (count < 2) 0 else count
        val dotsWidth = if (dots == 0) 0f else dots * radius * 2 + (dots - 1) * gap
        val hintWidth = if (chartHint) hintGap + chevronWidth else 0f
        val left = (width - (dotsWidth + hintWidth)) / 2f
        val centreY = height / 2f

        var x = left + radius
        for (i in 0 until dots) {
            canvas.drawCircle(x, centreY, radius, if (i == selected) activePaint else idlePaint)
            x += radius * 2 + gap
        }

        if (!chartHint) return
        val chevronX = left + dotsWidth + hintGap
        val top = if (chartOpen) {
            centreY + chevronHeight / 2
        } else {
            centreY - chevronHeight / 2
        }
        val tip = if (chartOpen) {
            centreY - chevronHeight / 2
        } else {
            centreY + chevronHeight / 2
        }
        chevronPath.reset()
        chevronPath.moveTo(chevronX, top)
        chevronPath.lineTo(chevronX + chevronWidth / 2, tip)
        chevronPath.lineTo(chevronX + chevronWidth, top)
        canvas.drawPath(chevronPath, chevronPaint)
    }
}
