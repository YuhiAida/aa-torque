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
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Spike for the screenshot plan in docs/ci-testing.md.
 *
 * Question: does Robolectric native graphics render SpeedView (TorqueSpeedometer) faithfully?
 * It inflates the real `fragment_gauge` layout with a theme, draws it, writes a PNG under
 * src/test/screenshots, and fails if the image is blank.
 *
 * This is throwaway validation. If it renders, the real test will use Roborazzi's
 * captureRoboImage + goldens instead of the manual draw below.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ThemeRenderSpikeTest {

    private fun slug(theme: String) = theme.lowercase().replace(" ", "_").replace("/", "_")

    private fun render(theme: String, size: Int = 480): Pair<File, Bitmap> {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        // A ContextThemeWrapper is what an Activity uses; setTheme on the application
        // context does not reach the views' theme-attribute lookups.
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
        // The layout's attributes are @{} expressions; setting the variables schedules a rebind.
        // Run it now so the theme's background and attrs are applied before the draw.
        binding.executePendingBindings()

        val view: View = binding.root
        view.measure(
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, size, size)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))

        val file = File("src/test/screenshots/${slug(theme)}.png")
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file to bitmap
    }

    private fun assertNotBlank(bitmap: Bitmap, theme: String) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val distinct = pixels.toSet().size
        assertTrue("$theme rendered blank ($distinct distinct colours)", distinct > 10)
    }

    @Test
    fun `renders porsche 982`() {
        val (file, bitmap) = render("Porsche 718")
        assertTrue("PNG missing", file.exists() && file.length() > 0)
        assertNotBlank(bitmap, "Porsche 718")
    }

    @Test
    fun `renders audi`() {
        val (file, bitmap) = render("Audi")
        assertTrue("PNG missing", file.exists() && file.length() > 0)
        assertNotBlank(bitmap, "Audi")
    }
}
