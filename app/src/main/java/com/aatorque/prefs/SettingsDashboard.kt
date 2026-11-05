package com.aatorque.prefs

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.aatorque.prefs.BackgroundPreference
import com.aatorque.prefs.BackgroundSource
import com.aatorque.prefs.BackgroundStore
import com.aatorque.prefs.encodeFile
import com.aatorque.prefs.parseBackground
import com.aatorque.stats.PidInfo
import com.aatorque.stats.R
import com.aatorque.stats.TorqueServiceWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class SettingsDashboard : PreferenceFragmentCompat() {

    lateinit var performanceTitle: EditTextPreference
    lateinit var mainCat: PreferenceCategory
    lateinit var optionsCat: PreferenceCategory
    lateinit var backgroundPref: BackgroundPreference

    val clockText = arrayOf(
        R.string.pref_leftclock,
        R.string.pref_centerclock,
        R.string.pref_rightclock
    )
    val clockIcon = arrayOf(
        R.drawable.ic_settings_clockl,
        R.drawable.ic_settings_clockc,
        R.drawable.ic_settings_clockr
    )
    val displayIcon = arrayOf(
        R.drawable.ic_settings_view1,
        R.drawable.ic_settings_view2,
        R.drawable.ic_settings_view3,
        R.drawable.ic_settings_view4
    )
    var mBound = false
    var torqueService: TorqueServiceWrapper? = null

    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) {
                Toast.makeText(
                    requireContext(),
                    R.string.background_import_failed,
                    Toast.LENGTH_SHORT
                )
                    .show()
                return@registerForActivityResult
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val name = BackgroundStore.import(requireContext(), uri)
                if (name == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(),
                            R.string.background_import_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }
                val value = encodeFile(name)
                val dbIndex = dashboardIndex()
                requireContext().dataStore.updateData { prefs ->
                    val screen = prefs.getScreens(dbIndex).toBuilder().setBackground(value).build()
                    prefs.toBuilder().setScreens(dbIndex, screen).build()
                }
                val prefs = requireContext().dataStore.data.first()
                val referenced = prefs.screensList.mapNotNull { screen ->
                    val src = parseBackground(screen.background)
                    if (src is BackgroundSource.Stored) src.name else null
                }.toSet()
                BackgroundStore.sweep(requireContext(), referenced)
                withContext(Dispatchers.Main) {
                    updateBackgroundSummary(value)
                }
            }
        }

    var torqueConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            mBound = true
            torqueService = (service as TorqueServiceWrapper.LocalBinder).getService()
            torqueService?.loadPidInformation(false) { pids ->
                buildOptions(pids)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mBound = false
        }
    }

    fun dashboardIndex(): Int =
        requireArguments().getCharSequence("prefix")?.split("_")!!.last().toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBound = TorqueServiceWrapper.runStartIntent(requireContext(), torqueConnection)
        if (mBound) {
            buildOptions(torqueService?.pids ?: emptyList())
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.display_setting)
        mainCat = findPreference("displayCat")!!
        optionsCat = findPreference("displayOptions")!!
        performanceTitle = findPreference("performanceTitle")!!
        performanceTitle.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
        performanceTitle.setOnPreferenceChangeListener { _, newValue ->
            lifecycleScope.launch {
                requireContext().dataStore.updateData {
                    val screen =
                        it.getScreens(dashboardIndex()).toBuilder().setTitle(newValue as String)
                    return@updateData it.toBuilder().setScreens(dashboardIndex(), screen).build()
                }
            }
            return@setOnPreferenceChangeListener true
        }
        backgroundPref = findPreference("background")!!
        backgroundPref.onChooseImage = { imagePicker.launch(arrayOf("image/*")) }
        backgroundPref.setOnPreferenceChangeListener { _, newValue ->
            lifecycleScope.launch {
                requireContext().dataStore.updateData { prefs ->
                    val screen =
                        prefs.getScreens(
                            dashboardIndex()
                        ).toBuilder().setBackground(newValue as String)
                    return@updateData prefs.toBuilder().setScreens(dashboardIndex(), screen).build()
                }
            }
            return@setOnPreferenceChangeListener true
        }
        lifecycleScope.launch {
            requireContext().dataStore.data.collect { prefs ->
                val screen = prefs.getScreens(dashboardIndex())
                backgroundPref.value = screen.background
                updateBackgroundSummary(screen.background)
            }
        }
    }

    private fun buildOptions(pids: PidInfo) {
        activity?.let { act ->
            act.runOnUiThread {
                mainCat.title = null
                val dbIndex = dashboardIndex()
                lifecycleScope.launch {
                    requireContext().dataStore.data.collect { userPreference ->
                        val screen = userPreference.getScreens(dbIndex)
                        performanceTitle.text = screen.title
                        performanceTitle.title =
                            resources.getString(
                                R.string.pref_title_performance,
                                dbIndex + 1
                            )
                        optionsCat.removeAll()
                        val sources = arrayOf(screen.gaugesList, screen.displaysList)
                        val texts = arrayOf(
                            clockText.map(requireContext()::getString),
                            (1..4).map {
                                resources.getString(R.string.pref_view, it)
                            }
                        )
                        val icons = arrayOf(clockIcon, displayIcon)
                        arrayOf("clock", "display").forEachIndexed { i, type ->
                            sources[i].forEachIndexed { j, screen ->
                                optionsCat.addPreference(
                                    Preference(requireContext()).also {
                                        it.key = "${type}_${dbIndex}_$j"
                                        it.summary = pids.firstOrNull { pid ->
                                            "torque_${pid.first}" == screen.pid
                                        }?.second?.get(0) ?: ""
                                        it.title = texts[i][j]
                                        it.icon = AppCompatResources.getDrawable(
                                            requireContext(),
                                            icons[i][j]
                                        )
                                        DrawableCompat.setTint(
                                            it.icon!!,
                                            resources.getColor(
                                                R.color.tintColor,
                                                requireContext().theme
                                            )
                                        )
                                        it.fragment =
                                            SettingsPIDFragment::class.java.canonicalName
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateBackgroundSummary(value: String) {
        val bundledTitles = resources.getStringArray(R.array.backgroundstrings)
        val bundledDrawables = resources.getStringArray(R.array.backgrounds)
        val map = bundledDrawables.mapIndexed { i, name ->
            name to
                bundledTitles.getOrElse(i) { name }
        }.toMap()
        val summary = when (val src = parseBackground(value)) {
            is BackgroundSource.Inherit -> getString(R.string.pref_background_summary_default)

            is BackgroundSource.Drawable -> map[src.name] ?: src.name

            is BackgroundSource.Stored -> {
                val index = BackgroundStore.list(requireContext()).indexOf(src.name)
                if (index >= 0) {
                    getString(R.string.custom_image_title, index + 1)
                } else {
                    getString(R.string.pref_background_summary_default)
                }
            }
        }
        backgroundPref.summary = summary
    }

    override fun onDestroy() {
        if (mBound) {
            try {
                requireActivity().unbindService(torqueConnection)
            } catch (e: IllegalArgumentException) {
                Timber.e("Failed to unbind service", e)
            }
            mBound = false
        }
    }
}
