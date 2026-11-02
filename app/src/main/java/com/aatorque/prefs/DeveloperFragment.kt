package com.aatorque.prefs

import android.os.Bundle
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceFragmentCompat
import com.aatorque.stats.R

/**
 * Developer options. Every tool is a direct switch; there is no master switch.
 */
class DeveloperFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.developer)
        val simulate = findPreference<CheckBoxPreference>(DevOptions.SIMULATE_VALUES)!!
        simulate.isChecked = DevOptions.simulateValues(requireContext())
        simulate.setOnPreferenceChangeListener { _, newValue ->
            DevOptions.setSimulateValues(requireContext(), newValue as Boolean)
            true
        }
    }
}
