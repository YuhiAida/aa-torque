package com.aatorque.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The card's text and frame ring are plain Kotlin, so they are tested here rather than on a
 * device. These are the parts a driver reads: the two lines beside the artwork, and the frame
 * Android Auto gets back when it asks for the art it was handed.
 */
class CardTextTest {

    private val metrics = listOf(
        CardMetric("RPM", "2140", 0f, 8000f, 0.26f),
        CardMetric("BOOST", "0.8 bar", -1f, 2f, 0.6f),
        CardMetric("OIL", "99 C", 0f, 150f, 0.66f)
    )

    @Test
    fun subtitleUsesTheFirstTwoMetrics() {
        assertEquals("RPM 2140 | BOOST 0.8 bar", CardText.subtitle(metrics))
    }

    @Test
    fun subtitleSurvivesAnEmptyDashboard() {
        assertEquals("", CardText.subtitle(emptyList()))
    }

    @Test
    fun descriptionFollowsTheEcuState() {
        assertEquals("ECU live", CardText.description(ecuLive = true, hasValues = true))
        assertEquals(
            "ECU connected - no data yet",
            CardText.description(ecuLive = true, hasValues = false)
        )
        assertEquals(
            "No ECU - open Torque",
            CardText.description(ecuLive = false, hasValues = false)
        )
    }

    @Test
    fun aBenchWithoutAnEcuNeverLooksLive() {
        // Values without a live ECU are dashes, and the description says so.
        val dashes = listOf(CardMetric("RPM", CardService.NO_VALUE, 0f, 8000f, 0f))
        assertEquals("-", dashes.first().value)
        assertEquals(
            "No ECU - open Torque",
            CardText.description(
                ecuLive = false,
                hasValues = dashes.any {
                    it.value !=
                        CardService.NO_VALUE
                }
            )
        )
    }

    @Test
    fun publishedFramesComeBackByTheirOwnId() {
        CardFrames.clear()
        val first = snapshot("first")
        val second = snapshot("second")
        CardFrames.publish(1, first)
        CardFrames.publish(2, second)

        assertEquals(first, CardFrames.snapshot(1))
        assertEquals(second, CardFrames.snapshot(2))
        assertNull(CardFrames.snapshot(3))
    }

    @Test
    fun oldFramesAreDroppedSoMemoryStaysBounded() {
        CardFrames.clear()
        repeat(200) { CardFrames.publish(it, snapshot("frame $it")) }

        assertNull("the oldest frames must be gone", CardFrames.snapshot(0))
        assertEquals("frame 199", CardFrames.snapshot(199)?.subtitle)
    }

    @Test
    fun clearForgetsEverything() {
        CardFrames.publish(7, snapshot("seven"))
        CardFrames.clear()
        assertNull(CardFrames.snapshot(7))
    }

    private fun snapshot(subtitle: String) = CardSnapshot(
        style = CardStyle.NUMBERS,
        theme = "VW",
        metrics = metrics,
        subtitle = subtitle,
        description = "ECU live"
    )
}
