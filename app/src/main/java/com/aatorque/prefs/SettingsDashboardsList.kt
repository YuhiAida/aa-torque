package com.aatorque.prefs

import android.os.Bundle
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.aatorque.datastore.UserPreference
import com.aatorque.prefs.UserPreferenceSerializer
import com.aatorque.stats.R
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import timber.log.Timber

class SettingsDashboardsList : PreferenceFragmentCompat() {
    lateinit var shownInCar: PreferenceCategory
    lateinit var countPref: EditTextPreference
    private var currentCount: Int = 1

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.dashboards_setting)
        shownInCar = findPreference("shownInCar")!!
        countPref = findPreference("dashboardCount")!!
        countPref.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
        countPref.setOnBindEditTextListener {
            it.inputType = InputType.TYPE_CLASS_NUMBER
        }
        countPref.setOnPreferenceChangeListener { _, newValue ->
            val intVal = screensCountOrNull(newValue as? String)
                ?: return@setOnPreferenceChangeListener false
            if (intVal >= currentCount) {
                applyCount(intVal)
                return@setOnPreferenceChangeListener true
            }
            AlertDialog.Builder(requireContext())
                .setMessage(R.string.pref_count_warning)
                .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
                .setPositiveButton(R.string.action_delete) { dialog, _ ->
                    dialog.dismiss()
                    applyCount(intVal)
                    countPref.text = intVal.toString()
                }
                .show()
            return@setOnPreferenceChangeListener false
        }

        lifecycleScope.launch {
            requireContext().dataStore.data.distinctUntilChangedBy {
                it.screensList.map { it.title }
            }.collect { userPreference ->
                currentCount = userPreference.screensCount
                countPref.text = userPreference.screensCount.toString()
                shownInCar.removeAll()
                userPreference.screensList.forEachIndexed { i, screen ->
                    shownInCar.addPreference(
                        Preference(requireContext()).also {
                            it.title = requireContext().getString(
                                R.string.pref_data_element_settings,
                                i + 1
                            )
                            it.key = "dashboard_$i"
                            it.fragment = SettingsDashboard::class.java.canonicalName
                            it.summary = screen.title
                        }
                    )
                }
            }
        }
    }

    private fun applyCount(intVal: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            requireContext().dataStore.updateData { currentSettings ->
                var bldr = currentSettings.toBuilder()
                if (bldr.screensCount > intVal) {
                    val keeping = bldr.screensList.subList(0, intVal)
                    bldr = bldr.clearScreens().addAllScreens(keeping)
                } else if (currentSettings.screensCount < intVal) {
                    val newItms = Collections.nCopies(
                        intVal - currentSettings.screensCount,
                        UserPreferenceSerializer.defaultScreen.build()
                    )
                    bldr = bldr.addAllScreens(newItms.toMutableList())
                    Timber.i("${newItms.size} added, $intVal specified")
                }
                return@updateData bldr.build()
            }
        }
    }
}
