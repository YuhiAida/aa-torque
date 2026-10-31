package com.aatorque.stats

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every media card style at the pixel size Android Auto gives the artwork and
 * compares it against the committed golden in `app/src/test/screenshots/card_*.png`.
 *
 * Record: `./gradlew recordRoborazziDebug`
 * Verify: `./gradlew verifyRoborazziDebug` (what CI runs)
 *
 * No device, no Android Auto, no Torque binding: [CardArtwork.render] is a pure function.
 * This is rung 1 of the test plan in issue #60. It proves legibility and layout, not the
 * frame rate Android Auto accepts — only the unit can prove that.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CardArtworkTest {

    private val size = 512
    private val goldenDir = "src/test/screenshots/cards"
    private val theme = "VW"

    private val metrics = listOf(
        CardMetric("RPM", "2140", 0f, 8000f, 0.2675f),
        CardMetric("BOOST", "0.8 bar", -1f, 2f, 0.60f),
        CardMetric("OIL", "99 C", 0f, 150f, 0.66f),
        CardMetric("COOLANT", "87 C", 0f, 150f, 0.58f),
        CardMetric("VOLTAGE", "14.1 V", 10f, 16f, 0.6833f)
    )

    private fun slug(style: CardStyle) = "card_${style.name.lowercase()}"

    @Test
    fun everyCardStyleRendersAndMatchesItsGolden() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val recording = System.getProperty("roborazzi.test.record") == "true"

        for (style in CardStyle.values()) {
            val bitmap = CardArtwork.render(appContext, theme, style, metrics, size)

            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue("$style rendered blank", pixels.toSet().size > 10)

            bitmap.captureRoboImage("$goldenDir/${slug(style)}.png")
        }

        if (!recording) {
            val expected = CardStyle.values().map { "${slug(it)}.png" }.toSet()
            val actual = File(goldenDir)
                .listFiles()
                ?.filter { it.extension == "png" && it.name.startsWith("card_") }
                ?.map { it.name }
                ?.toSet()
                ?: emptySet()
            assertEquals(
                "missing card goldens (record them)",
                emptySet<String>(),
                expected - actual
            )
            assertEquals(
                "orphan card goldens (style removed?)",
                emptySet<String>(),
                actual - expected
            )
        }
    }

    @Test
    fun theBottomThirdStaysEmptyForTheAndroidAutoControls() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()

        for (style in CardStyle.values()) {
            val bitmap = CardArtwork.render(appContext, theme, style, metrics, size)
            val background = bitmap.getPixel(2, 2)
            val top = size * 0.66
            var painted = 0

            for (y in top.toInt() until size) {
                for (x in 0 until size) {
                    if (bitmap.getPixel(x, y) != background) {
                        painted++
                    }
                }
            }

            assertEquals("$style draws into AA's control area", 0, painted)
        }
    }
}
