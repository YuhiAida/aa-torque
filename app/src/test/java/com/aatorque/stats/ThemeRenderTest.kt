package com.aatorque.stats

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aatorque.datastore.MaxControl
import com.aatorque.prefs.mapTheme
import com.aatorque.stats.databinding.FragmentGaugeBinding
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every theme in `R.array.Themes` with the real `fragment_gauge` layout and
 * compares it against the committed golden in `app/src/test/screenshots/`.
 *
 * Record: `./gradlew recordRoborazziDebug`
 * Verify: `./gradlew verifyRoborazziDebug` (what CI runs)
 *
 * A new theme has no golden, so the verify run fails with "missing goldens" until the
 * author records it. The recorded PNG is then committed and shows up in the PR diff.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ThemeRenderTest {

    private val size = 480
    private val goldenDir = "src/test/screenshots"

    private fun slug(theme: String) = theme.lowercase().replace(" ", "_").replace("/", "_")

    private fun render(appContext: Context, theme: String): Bitmap {
        val context = android.view.ContextThemeWrapper(appContext, mapTheme(appContext, theme))
        val binding = FragmentGaugeBinding.inflate(LayoutInflater.from(context))
        binding.minValue = 0f
        binding.maxValue = 100f
        binding.minMax = Pair(0f, 100f)
        binding.ticksOn = true
        binding.wholeNumbers = true
        binding.title = theme
        binding.limitValue = "50"
        binding.minMaxBelow = false
        binding.showLimitMarked = MaxControl.OFF
        binding.showLimitBelow = MaxControl.OFF
        binding.font = Typeface.DEFAULT
        binding.tickFormatter = { _, speed -> speed.toString() }
        // The layout's attributes are @{} expressions; flush them so the theme's dial
        // background is applied before the draw.
        binding.executePendingBindings()

        // Apply the theme's needle, which the fragment does in TorqueGauge.turnRaysEnabled.
        val dial = binding.dial
        val needle =
            context.theme.obtainStyledAttributes(intArrayOf(R.attr.themedNeedle)).getDrawable(0)
        if (needle != null) {
            dial.indicator = SizedImageIndicator(context, needle)
        }
        dial.setSpeedAt(50f)

        val view = binding.root
        view.measure(
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, size, size)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    @Test
    fun everyThemeRendersAndMatchesItsGolden() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val themes = appContext.resources.getStringArray(R.array.Themes)
        val recording = System.getProperty("roborazzi.test.record") == "true"

        for (theme in themes) {
            val bitmap = render(appContext, theme)

            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue("$theme rendered blank", pixels.toSet().size > 10)

            // The Bitmap overload is used because the View overload requires an Activity
            // context, and these views are themed over the application context.
            bitmap.captureRoboImage("$goldenDir/${slug(theme)}.png")
        }

        if (!recording) {
            val expected = themes.map { "${slug(it)}.png" }.toSet()
            val actual = File(goldenDir)
                .listFiles()
                ?.filter { it.extension == "png" }
                ?.map { it.name }
                ?.toSet()
                ?: emptySet()
            assertEquals("missing goldens (record them)", emptySet<String>(), expected - actual)
            assertEquals("orphan goldens (theme removed?)", emptySet<String>(), actual - expected)
        }
    }
}
