package com.aatorque.stats

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aatorque.prefs.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The dev channel installs beside the stable app, so its launcher name has to say which one
 * is running. Both names come from manifest placeholders in `app/build.gradle`; a refactor
 * that drops one would silently bring back two apps called AA Torque.
 *
 * This lives in the `testDebug` source set on purpose: the shared `test` source set also runs
 * for `release` and `release_debug`, where the label is `AA Torque` and these assertions fail.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DebugLabelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageManager: PackageManager = context.packageManager

    @Test
    fun debugBuildIsNamedAsDebug() {
        val info = packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals("AA Torque Debug", packageManager.getApplicationLabel(info).toString())
    }

    @Test
    fun launcherEntryIsNamedAsDebug() {
        val launcher = ComponentName(context, SettingsActivity::class.java)
        val info = packageManager.getActivityInfo(launcher, 0)
        assertEquals("AA Torque Debug", info.loadLabel(packageManager).toString())
    }
}
