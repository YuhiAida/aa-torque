package com.aatorque.prefs

import android.app.Dialog
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.preference.Preference
import com.aatorque.stats.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * Guards the dashboard settings screen and its background picker.
 *
 * The first case is a regression test for a crash where onCreatePreferences touched the
 * fragment view lifecycle before the view existed, which killed the screen on open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsDashboardTest {

    private fun buildFragment(): SettingsDashboard {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val fragment = SettingsDashboard().apply {
            arguments = Bundle().apply { putString("prefix", "dashboard_0") }
        }
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.settings_fragment, fragment)
            .commitNow()
        return fragment
    }

    @Test
    fun buildsAndExposesTheBackgroundRow() {
        val fragment = buildFragment()
        assertTrue(
            fragment.findPreference<Preference>("background") is BackgroundPreference
        )
    }

    @Test
    fun pickerOffersEverySourceAndTheImageChooser() {
        val fragment = buildFragment()
        val pref = fragment.findPreference<Preference>("background") as BackgroundPreference
        val labels = fragment.requireContext()

        pref.performClick()

        // The dialog is built after the thumbnails load on an IO thread.
        var dialog: Dialog? = null
        for (attempt in 0 until 40) {
            shadowOf(Looper.getMainLooper()).idle()
            dialog = ShadowDialog.getLatestDialog()
            if (dialog != null) break
            Thread.sleep(50)
        }
        assertNotNull("the picker dialog never opened", dialog)

        val list = findListView(dialog!!.window!!.decorView)
        assertNotNull("the picker dialog holds no list", list)
        val rows = (0 until list!!.adapter.count).map {
            (list.adapter.getItem(it) as BackgroundRow).title
        }

        assertEquals(BackgroundRow.Kind.DEFAULT, (list.adapter.getItem(0) as BackgroundRow).kind)
        assertTrue(
            "no image chooser row",
            rows.contains(labels.getString(R.string.pref_background_choose))
        )
        assertTrue(
            "the bundled list is empty",
            rows.contains(labels.getString(R.string.pref_background_default))
        )
    }

    private fun findListView(view: View): ListView? {
        if (view is ListView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findListView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
