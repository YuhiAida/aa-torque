package com.aatorque.stats

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.aatorque.prefs.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Draws the three card styles on the phone, with sample values.
 *
 * Android Auto is the real surface, but needing a head unit to look at a card is a bad loop.
 * This screen calls the same [CardArtwork.render] the card service calls, at the same pixel
 * size, so what it shows is what AA will paint.
 */
class CardPreviewActivity : Activity() {

    private val sample = listOf(
        CardMetric("RPM", "2140", 0f, 8000f, 0.2675f),
        CardMetric("BOOST", "0.8 bar", -1f, 2f, 0.6f),
        CardMetric("OIL", "99 C", 0f, 150f, 0.66f),
        CardMetric("COOLANT", "87 C", 0f, 150f, 0.58f),
        CardMetric("VOLTAGE", "14.1 V", 10f, 16f, 0.6833f)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.card_preview_title)

        val prefs = runBlocking { applicationContext.dataStore.data.first() }
        val theme = prefs.selectedTheme.ifEmpty { resources.getStringArray(R.array.Themes).first() }
        val density = resources.displayMetrics.density
        val pad = (12 * density).toInt()

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        for (style in CardStyle.values()) {
            column.addView(
                TextView(this).apply {
                    text = style.name.lowercase().replace('_', ' ')
                    textSize = 16f
                    setPadding(0, pad, 0, pad / 2)
                }
            )
            column.addView(
                ImageView(this).apply {
                    setImageBitmap(
                        CardArtwork.render(this@CardPreviewActivity, theme, style, sample, 512)
                    )
                    adjustViewBounds = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
            )
        }
        column.addView(
            TextView(this).apply {
                text = getString(R.string.card_preview_note)
                textSize = 13f
                setPadding(0, pad, 0, pad)
            }
        )
        setContentView(ScrollView(this).apply { addView(column) })
    }
}
