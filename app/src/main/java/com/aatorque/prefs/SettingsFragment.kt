package com.aatorque.prefs

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.lifecycle.lifecycleScope
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import com.aatorque.datastore.UserPreference
import com.aatorque.stats.NotiService
import com.aatorque.stats.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class SettingsFragment : PreferenceFragmentCompat() {
    lateinit var dashboardsEntry: Preference
    lateinit var backgroundPref: ImageListPreference
    lateinit var themePref: ImageListPreference
    lateinit var fontPref: ImageListPreference
    lateinit var centerGaugeLargePref: CheckBoxPreference
    lateinit var minMaxBelowPref: CheckBoxPreference
    lateinit var mediaBgPref: CheckBoxPreference
    lateinit var opacityPref: SeekBarPreference
    lateinit var darkenArtPref: SeekBarPreference
    lateinit var blurArtPref: SeekBarPreference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager.sharedPreferencesName = null
        dashboardsEntry = findPreference("dashboardsEntry")!!
        backgroundPref = findPreference("selectedBackground")!!
        themePref = findPreference("selectedTheme")!!
        fontPref = findPreference("selectedFont")!!
        centerGaugeLargePref = findPreference("centerGaugeLarge")!!
        minMaxBelowPref = findPreference("minMaxBelow")!!
        mediaBgPref = findPreference("mediaBg")!!
        opacityPref = findPreference("gaugeOpacity")!!
        blurArtPref = findPreference("blurArtwork")!!
        darkenArtPref = findPreference("darkenArtwork")!!
        themePref.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        fontPref.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        backgroundPref.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        themePref.setOnPreferenceChangeListener { _, newValue ->
            updateDatastorePref {
                it.setSelectedTheme(newValue as String)
            }
            return@setOnPreferenceChangeListener true
        }
        fontPref.setOnPreferenceChangeListener { _, newValue ->
            updateDatastorePref {
                it.setSelectedFont(newValue as String)
            }
            Timber.i("Setting font $newValue")
            return@setOnPreferenceChangeListener true
        }
        backgroundPref.setOnPreferenceChangeListener { _, newValue ->
            updateDatastorePref {
                it.setSelectedBackground(newValue as String)
            }
            return@setOnPreferenceChangeListener true
        }
        centerGaugeLargePref.setOnPreferenceChangeListener { _, newValue ->
            updateDatastorePref {
                it.setCenterGaugeLarge(newValue as Boolean)
            }
            return@setOnPreferenceChangeListener true
        }
        minMaxBelowPref.setOnPreferenceChangeListener { _, newValue ->
            updateDatastorePref {
                it.setMinMaxBelow(newValue as Boolean)
            }
            return@setOnPreferenceChangeListener true
        }
        mediaBgPref.setOnPreferenceChangeListener { _, newValue ->
            updateDatastorePref {
                if (newValue as Boolean &&
                    !NotiService.isNotificationAccessEnabled(requireContext())
                ) {
                    startActivity(
                        Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                putExtra(
                                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                                    NotiService::class.java.canonicalName
                                )
                            }
                        }
                    )
                }
                it.setAlbumArt(newValue)
            }
            return@setOnPreferenceChangeListener true
        }
        opacityPref.setOnPreferenceChangeListener { _, newValue ->
            updateDatastorePref {
                it.setOpacity(newValue as Int)
            }
            return@setOnPreferenceChangeListener true
        }
        darkenArtPref.setOnPreferenceChangeListener { _, newValue ->
            updateDatastorePref {
                it.setDarkenArt(newValue as Int)
            }
            return@setOnPreferenceChangeListener true
        }
        blurArtPref.setOnPreferenceChangeListener { _, newValue ->
            updateDatastorePref {
                it.setBlurArt(newValue as Int)
            }
            return@setOnPreferenceChangeListener true
        }
        blurArtPref.isVisible = Build.VERSION.SDK_INT >= 31

        lifecycleScope.launch {
            requireContext().dataStore.data.collect {
                themePref.value = it.selectedTheme
                fontPref.value = it.selectedFont
                backgroundPref.value = it.selectedBackground
                centerGaugeLargePref.isChecked = it.centerGaugeLarge
                minMaxBelowPref.isChecked = it.minMaxBelow
                mediaBgPref.isChecked =
                    it.albumArt && NotiService.isNotificationAccessEnabled(requireContext())
                opacityPref.value = if (it.opacity == 0) 100 else it.opacity
                blurArtPref.value = it.blurArt
                darkenArtPref.value = it.darkenArt
            }
        }
        lifecycleScope.launch {
            requireContext().dataStore.data.collect { prefs ->
                val count = prefs.screensCount
                dashboardsEntry.summary =
                    resources.getQuantityString(R.plurals.dashboards_count, count, count)
            }
        }
    }

    private fun updateDatastorePref(
        updateBuilder: (obj: UserPreference.Builder) -> UserPreference.Builder
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            requireContext().dataStore.updateData { currentSettings ->
                updateBuilder(currentSettings.toBuilder()).build()
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings)
    }

    override fun onStart() {
        super.onStart()
        (requireActivity() as SettingsActivity).supportActionBar!!.subtitle = null
    }
}
