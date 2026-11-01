package com.aatorque.stats

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import com.aatorque.datastore.MaxControl
import com.aatorque.prefs.mapTheme
import com.aatorque.stats.databinding.FragmentGaugeBinding

/**
 * Which arrangement the Android Auto media card shows.
 *
 * The card is small (the Coolwalk rail is about 31% of the display width), so the
 * dashboard layout cannot be scaled down into it. These three styles are drawn at
 * the card size instead. See issue #60.
 */
enum class CardStyle { NUMBERS, ONE_DIAL, TWO_DIALS }

/**
 * One metric, already formatted.
 *
 * [at] is the needle position as a fraction of [min]..[max]. It is ignored by
 * [CardStyle.NUMBERS].
 */
data class CardMetric(
    val title: String,
    val value: String,
    val min: Float = 0f,
    val max: Float = 100f,
    val at: Float = 0f
)

/**
 * Renders the media card artwork.
 *
 * Android Auto paints the returned bitmap full-bleed behind its own title/subtitle
 * lines and draws the playback controls at the bottom, so the cluster stays in the
 * upper two thirds and the bottom third is left empty on purpose.
 *
 * Pure function of its arguments: no Torque binding, no lifecycle, no Android Auto.
 * That is what lets the JVM test render it without a device.
 */
object CardArtwork {

    /** Fraction of the card kept clear at the bottom for AA's controls. */
    private const val CONTROLS_KEEP_OUT = 0.34f

    fun render(
        context: Context,
        theme: String,
        style: CardStyle,
        metrics: List<CardMetric>,
        size: Int
    ): Bitmap {
        val themed = ContextThemeWrapper(context, mapTheme(context, theme))
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColour(themed))
        when (style) {
            CardStyle.NUMBERS -> drawNumbers(canvas, themed, metrics, size)
            CardStyle.ONE_DIAL -> drawDials(canvas, themed, metrics.take(1), size, 1)
            CardStyle.TWO_DIALS -> drawDials(canvas, themed, metrics.take(2), size, 2)
        }
        return bitmap
    }

    private fun drawNumbers(
        canvas: Canvas,
        context: Context,
        metrics: List<CardMetric>,
        size: Int
    ) {
        val rows = metrics.take(5)
        if (rows.isEmpty()) {
            return
        }
        val height = size * (1f - CONTROLS_KEEP_OUT - 0.10f)
        val rowHeight = height / rows.size
        val label = textPaint(context, size * 0.072f, android.R.attr.textColorSecondary)
        val value = textPaint(context, size * 0.105f, android.R.attr.textColorPrimary)
        value.typeface = Typeface.DEFAULT_BOLD
        val left = size * 0.08f
        val right = size * 0.92f
        rows.forEachIndexed { index, metric ->
            val baseline = size * 0.10f + rowHeight * (index + 0.72f)
            canvas.drawText(metric.title.uppercase(), left, baseline, label)
            value.textAlign = Paint.Align.RIGHT
            canvas.drawText(metric.value, right, baseline, value)
            value.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawDials(
        canvas: Canvas,
        context: Context,
        metrics: List<CardMetric>,
        size: Int,
        count: Int
    ) {
        if (metrics.isEmpty()) {
            return
        }
        val dialSize = if (count == 1) (size * 0.52f) else (size * 0.44f)
        val gap = (size - dialSize * metrics.size) / (metrics.size + 1)
        val label = textPaint(context, size * 0.075f, android.R.attr.textColorPrimary)
        label.textAlign = Paint.Align.CENTER
        val top = size * 0.04f
        metrics.forEachIndexed { index, metric ->
            val view = gauge(context, metric, dialSize.toInt())
            val x = gap + index * (dialSize + gap)
            canvas.save()
            canvas.translate(x, top)
            view.draw(canvas)
            canvas.restore()
            // The theme's gauge does not print the metric name at this size, so the card does.
            canvas.drawText(
                metric.title.uppercase(),
                x + dialSize / 2f,
                top + dialSize + size * 0.075f,
                label
            )
        }
    }

    /** Builds a real themed gauge, exactly as the dashboard fragment does. */
    private fun gauge(context: Context, metric: CardMetric, sizePx: Int): View {
        val binding = FragmentGaugeBinding.inflate(LayoutInflater.from(context))
        binding.minValue = metric.min
        binding.maxValue = metric.max
        binding.minMax = Pair(metric.min, metric.max)
        binding.ticksOn = false
        binding.wholeNumbers = (metric.max - metric.min) >= 20f
        binding.title = metric.title
        binding.limitValue = ""
        binding.minMaxBelow = false
        binding.showLimitMarked = MaxControl.OFF
        binding.showLimitBelow = MaxControl.OFF
        binding.font = Typeface.DEFAULT
        binding.tickFormatter = { _, _ -> "" }
        binding.executePendingBindings()

        val needle =
            context.theme.obtainStyledAttributes(intArrayOf(R.attr.themedNeedle)).getDrawable(0)
        if (needle != null) {
            binding.dial.indicator = SizedImageIndicator(context, needle)
        }
        binding.dial.setSpeedAt(metric.min + (metric.max - metric.min) * metric.at)

        val root = binding.root
        root.measure(
            View.MeasureSpec.makeMeasureSpec(sizePx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(sizePx, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, sizePx, sizePx)
        return root
    }

    private fun textPaint(context: Context, sizePx: Float, colourAttr: Int): Paint {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = sizePx
        paint.color = colour(context, colourAttr)
        return paint
    }

    private fun colour(context: Context, attr: Int): Int {
        val array = context.obtainStyledAttributes(intArrayOf(attr))
        return try {
            array.getColor(0, Color.WHITE)
        } finally {
            array.recycle()
        }
    }

    private fun backgroundColour(context: Context): Int {
        val array = context.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground))
        return try {
            array.getColor(0, Color.BLACK)
        } finally {
            array.recycle()
        }
    }
}
