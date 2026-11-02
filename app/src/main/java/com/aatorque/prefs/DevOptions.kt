package com.aatorque.prefs

import android.content.Context
import com.aatorque.stats.BuildConfig

/**
 * Developer options.
 *
 * They live in their own SharedPreferences on purpose: they are not part of the dashboard
 * configuration, so they do not travel with an export, and the default can follow the build
 * type. Simulation defaults on in the debug packages and off in release; an explicit choice
 * by the user wins from then on.
 */
object DevOptions {
    const val FILE = "dev_options"
    const val SIMULATE_VALUES = "simulateValues"

    fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun simulateValues(context: Context): Boolean =
        prefs(context).getBoolean(SIMULATE_VALUES, BuildConfig.SIMULATE_METRICS)

    fun setSimulateValues(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(SIMULATE_VALUES, enabled).apply()
    }
}
