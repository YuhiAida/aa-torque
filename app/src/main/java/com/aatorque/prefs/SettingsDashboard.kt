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
import com.aatorque.datastore.UserPreference
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

class SettingsDashboard : PreferenceFragmentCompat() {

    lateinit var performanceTitle: EditTextPreference
    lateinit var mainCat: PreferenceCategory
    lateinit var gaugesCat: PreferenceCategory
    lateinit var displaysCat: PreferenceCategory
    lateinit var torqueStatus: Preference
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
    private var pids: PidInfo? = null
    private var lastPrefs: UserPreference? = null

    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) {
                return@registerForActivityResult
            }
            // The fragment can detach while a large image imports, and requireContext() would
            // then throw. The file work and the store only need the application context.
            val appContext = requireContext().applicationContext
            val dbIndex = dashboardIndex()
            lifecycleScope.launch(Dispatchers.IO) {
                backgroundFiles.withLock {
                    val name = BackgroundStore.import(appContext, uri)
                    if (name == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                appContext,
                                R.string.background_import_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@withLock
                    }
                    val value = encodeFile(name)
                    // If this coroutine dies before the update, the file stays unreferenced and
                    // the next sweep on this screen deletes it.
                    val prefs = appContext.dataStore.updateData { prefs ->
                        if (!prefs.hasScreen(dbIndex)) return@updateData prefs
                        val screen =
                            prefs.getScreens(dbIndex).toBuilder().setBackground(value).build()
                        prefs.toBuilder().setScreens(dbIndex, screen).build()
                    }
                    BackgroundStore.sweep(appContext, prefs.referencedBackgroundFiles())
                    val storedNames = BackgroundStore.list(appContext)
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            updateBackgroundSummary(value, "", storedNames)
                        }
                    }
                }
            }
        }

    var torqueConnection = object : ServiceConnection {
        // The framework can hand back a null binding when the service is not running, and a
        // null component on some OEM builds. Both are survivable: the PID list stays empty.
        override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
            mBound = true
            val binder = service as? TorqueServiceWrapper.LocalBinder ?: return
            torqueService = binder.getService()
            torqueService?.loadPidInformation(false) { newPids ->
                // The pid list arrives on a plain thread; preference writes must be on main.
                activity?.runOnUiThread {
                    pids = newPids
                    rebuildRows()
                    torqueStatus.summary = getString(R.string.status_torque_connected)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mBound = false
            torqueStatus.summary = getString(R.string.status_torque_waiting)
        }
    }

    fun dashboardIndex(): Int =
        requireArguments().getCharSequence("prefix")?.split("_")!!.last().toInt()

    /** The dashboard can be deleted while its screen is open, so the index may be stale. */
    private fun UserPreference.hasScreen(index: Int): Boolean = index in 0 until screensCount

    /**
     * Every stored background file the settings point at. The global selection counts: an
     * imported settings file can name a stored image there, and a sweep that ignored it would
     * delete the file and drop the dashboard back to black.
     */
    private fun UserPreference.referencedBackgroundFiles(): Set<String> {
        val perScreen = screensList.mapNotNull { screen ->
            (parseBackground(screen.background) as? BackgroundSource.Stored)?.name
        }
        val global = (parseBackground(selectedBackground) as? BackgroundSource.Stored)?.name
        return (perScreen + listOfNotNull(global)).toSet()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBound = TorqueServiceWrapper.runStartIntent(requireContext(), torqueConnection)
        startOptionsCollector()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.display_setting)
        mainCat = findPreference("thisDashboard")!!
        gaugesCat = findPreference("gaugesCat")!!
        displaysCat = findPreference("displaysCat")!!
        torqueStatus = findPreference("torqueStatus")!!
        torqueStatus.summary = getString(R.string.status_torque_waiting)
        performanceTitle = findPreference("performanceTitle")!!
        performanceTitle.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
        performanceTitle.setOnPreferenceChangeListener { _, newValue ->
            lifecycleScope.launch {
                val index = dashboardIndex()
                requireContext().dataStore.updateData {
                    if (!it.hasScreen(index)) return@updateData it
                    val screen = it.getScreens(index).toBuilder().setTitle(newValue as String)
                    return@updateData it.toBuilder().setScreens(index, screen).build()
                }
            }
            return@setOnPreferenceChangeListener true
        }
        backgroundPref = findPreference("background")!!
        // onCreatePreferences runs before the fragment view exists, so viewLifecycleOwner
        // throws here. The fragment lifecycle is the correct binding for the dialog work.
        backgroundPref.scope = lifecycleScope
        backgroundPref.onChooseImage = { imagePicker.launch(arrayOf("image/*")) }
        backgroundPref.setOnPreferenceChangeListener { _, newValue ->
            lifecycleScope.launch {
                backgroundFiles.withLock {
                    val index = dashboardIndex()
                    val prefs = requireContext().dataStore.updateData { prefs ->
                        if (!prefs.hasScreen(index)) return@updateData prefs
                        val screen =
                            prefs.getScreens(index).toBuilder().setBackground(newValue as String)
                        return@updateData prefs.toBuilder().setScreens(index, screen).build()
                    }
                    val referenced = prefs.referencedBackgroundFiles()
                    withContext(Dispatchers.IO) {
                        BackgroundStore.sweep(requireContext(), referenced)
                    }
                }
            }
            return@setOnPreferenceChangeListener true
        }
        lifecycleScope.launch {
            requireContext().dataStore.data.collect { prefs ->
                val index = dashboardIndex()
                if (!prefs.hasScreen(index)) return@collect
                val screen = prefs.getScreens(index)
                backgroundPref.value = screen.background
                val storedNames = withContext(Dispatchers.IO) {
                    BackgroundStore.list(requireContext())
                }
                updateBackgroundSummary(screen.background, prefs.selectedBackground, storedNames)
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            backgroundFiles.withLock {
                val prefs = requireContext().dataStore.data.first()
                BackgroundStore.sweep(requireContext(), prefs.referencedBackgroundFiles())
            }
        }
    }

    private fun startOptionsCollector() {
        lifecycleScope.launch {
            requireContext().dataStore.data.collect { userPreference ->
                lastPrefs = userPreference
                rebuildRows(userPreference)
            }
        }
    }

    private fun rebuildRows() {
        val up = lastPrefs
        if (up == null) {
            lifecycleScope.launch {
                lastPrefs = requireContext().dataStore.data.first()
                rebuildRows(lastPrefs!!)
            }
        } else {
            rebuildRows(up)
        }
    }

    private fun rebuildRows(userPreference: UserPreference) {
        activity?.let { act ->
            act.runOnUiThread {
                val dbIndex = dashboardIndex()
                if (!userPreference.hasScreen(dbIndex)) return@runOnUiThread
                val screen = userPreference.getScreens(dbIndex)
                val currentPids = pids
                performanceTitle.text = screen.title
                gaugesCat.removeAll()
                displaysCat.removeAll()
                val sources = arrayOf(screen.gaugesList, screen.displaysList)
                val texts = arrayOf(
                    clockText.map(requireContext()::getString),
                    (1..4).map {
                        resources.getString(R.string.pref_view, it)
                    }
                )
                val icons = arrayOf(clockIcon, displayIcon)
                val cats = arrayOf(gaugesCat, displaysCat)
                arrayOf("clock", "display").forEachIndexed { i, type ->
                    sources[i].forEachIndexed { j, disp ->
                        cats[i].addPreference(
                            Preference(requireContext()).also {
                                it.key = "${type}_${dbIndex}_$j"
                                it.summary = if (currentPids.isNullOrEmpty()) {
                                    if (disp.label.isBlank()) {
                                        getString(R.string.not_set)
                                    } else {
                                        disp.label
                                    }
                                } else {
                                    currentPids.firstOrNull { pid ->
                                        "torque_${pid.first}" == disp.pid
                                    }?.second?.firstOrNull() ?: ""
                                }
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

    private fun updateBackgroundSummary(value: String, storedNames: List<String>) =
        updateBackgroundSummary(value, "", storedNames)

    private fun updateBackgroundSummary(
        value: String,
        appBackground: String,
        storedNames: List<String>
    ) {
        val bundledTitles = resources.getStringArray(R.array.backgroundstrings)
        val bundledDrawables = resources.getStringArray(R.array.backgrounds)
        val map = bundledDrawables.mapIndexed { i, name ->
            name to
                bundledTitles.getOrElse(i) { name }
        }.toMap()

        fun bundledTitleFor(raw: String): String? {
            val name = if (raw.contains(":")) {
                val sep = raw.indexOf(":")
                val prefix = raw.substring(0, sep)
                val n = raw.substring(sep + 1)
                if (prefix == "resource" && n.isNotBlank()) n else null
            } else if (raw.isNotBlank()) {
                raw
            } else {
                null
            }
            if (name == null) return null
            val index = bundledDrawables.indexOf(name)
            return if (index >= 0) bundledTitles.getOrElse(index) { name } else null
        }

        val summary = when {
            // bare drawable or resource: resolves as bundled drawable
            !value.contains(":") && value.isNotBlank() -> {
                map[value] ?: value
            }

            else -> when (val src = parseBackground(value)) {
                is BackgroundSource.Inherit -> {
                    val inheritedTitle = bundledTitleFor(appBackground)
                    if (inheritedTitle != null) {
                        "${getString(R.string.pref_background_summary_default)} · $inheritedTitle"
                    } else {
                        getString(R.string.pref_background_summary_default)
                    }
                }

                is BackgroundSource.Drawable -> map[src.name] ?: src.name

                is BackgroundSource.Stored -> {
                    val index = storedNames.indexOf(src.name)
                    if (index >= 0) {
                        getString(R.string.custom_image_title, index + 1)
                    } else {
                        getString(R.string.pref_background_summary_default)
                    }
                }
            }
        }
        backgroundPref.summary = summary
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mBound) {
            try {
                requireActivity().unbindService(torqueConnection)
            } catch (e: IllegalArgumentException) {
                Timber.e("Failed to unbind service", e)
            }
            mBound = false
        }
    }

    companion object {
        // The import, the reference update and the cleanup run in three coroutines. One guard
        // stops a sweep from deleting an image before the reference that names it is stored.
        private val backgroundFiles = Mutex()
    }
}
