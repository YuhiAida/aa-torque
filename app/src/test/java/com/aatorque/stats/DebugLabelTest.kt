package com.aatorque.stats

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The dev channel installs beside the stable app, so its launcher name has to say which one
 * is running. The value comes from a manifest placeholder in `app/build.gradle`; a refactor
 * that drops the placeholder would silently bring back two apps called AA Torque. This runs
 * on the debug variant only, which is the one the tests and CI build.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DebugLabelTest {

    @Test
    fun debugBuildIsNamedAsDebug() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals(
            "AA Torque Debug",
            context.packageManager.getApplicationLabel(info).toString()
        )
    }
}
