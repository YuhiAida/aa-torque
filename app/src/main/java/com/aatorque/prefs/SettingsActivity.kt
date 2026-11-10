package com.aatorque.prefs

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.aatorque.datastore.UserPreference
import com.aatorque.stats.App
import com.aatorque.stats.BuildConfig
import com.aatorque.stats.CreditsFragment
import com.aatorque.stats.R
import com.google.protobuf.InvalidProtocolBufferException
import java.io.File
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

class SettingsActivity :
    AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar!!.setDisplayUseLogoEnabled(true)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_fragment, SettingsFragment())
                .commit()
        }
        lifecycleScope.launch(Dispatchers.IO) {
            checkUpdate()
        }
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                    super.onFragmentResumed(fm, f)
                    supportActionBar!!.subtitle = when (f) {
                        is SettingsDashboard -> {
                            resources.getString(
                                R.string.pref_data_element_settings,
                                f.dashboardIndex() + 1
                            )
                        }

                        is SettingsDashboardsList -> {
                            resources.getString(R.string.dashboards)
                        }

                        is SettingsPIDFragment -> {
                            f.requireArguments().getCharSequence("title")
                        }

                        is DeveloperFragment -> {
                            resources.getString(R.string.developer_options)
                        }

                        else -> {
                            null
                        }
                    }
                    supportActionBar!!.setDisplayHomeAsUpEnabled(f !is SettingsFragment)
                }
            },
            false
        )
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.settings_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.action_preview -> {
                launchFragment("preview", DashboardPreviewFragment())
                true
            }

            R.id.action_export_dashboards -> {
                // Export the file to a user provided location
                exportFile()
                true
            }

            R.id.action_import_dashboards -> {
                openFile()
                true
            }

            R.id.action_copy_logs -> {
                logsToClipboard()
                true
            }

            R.id.action_credits -> {
                launchFragment("credits", CreditsFragment())
                true
            }

            R.id.action_check_updates -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    if (!checkUpdate(force = true)) {
                        runOnUiThread {
                            Toast.makeText(
                                this@SettingsActivity,
                                R.string.update_check_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                true
            }

            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openFile() {
        importFileLauncher.launch(EXPORT_MIME)
    }

    private fun exportFile() {
        exportFileLauncher.launch("")
    }

    private fun launchFragment(tag: String, fragment: Fragment) {
        val existing = supportFragmentManager.findFragmentByTag(tag)
        if (existing?.isVisible == true) return
        supportFragmentManager.beginTransaction()
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .replace(
                R.id.settings_fragment,
                existing ?: fragment,
                tag
            )
            .addToBackStack(null)
            .commit()
    }

    private val exportFileLauncher = registerForActivityResult(ExportFileContract()) { uri: Uri? ->
        // This lambda will be executed when the activity result returns
        lifecycleScope.launch(Dispatchers.IO) {
            val data = applicationContext.dataStore.data.first()
            val result = if (uri != null) {
                try {
                    val outputStream = contentResolver.openOutputStream(uri)
                    if (outputStream == null) {
                        R.string.export_failed
                    } else {
                        outputStream.use { stream ->
                            UserPreferenceSerializer.writeTo(data, stream)
                        }
                        R.string.file_exported_successfully
                    }
                } catch (e: IOException) {
                    Timber.w(e, "Could not export settings file")
                    R.string.export_failed
                } catch (e: SecurityException) {
                    Timber.w(e, "No permission to export settings file")
                    R.string.export_failed
                }
            } else {
                R.string.export_failed
            }
            runOnUiThread {
                Toast.makeText(baseContext, result, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importFileLauncher = registerForActivityResult(ImportFileContract()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            // Read the file first. A file that does not parse must change nothing, so the
            // store is not touched until the user confirms.
            val imported = readImportedSettings(uri)
            if (imported == null) {
                Toast.makeText(baseContext, R.string.invalid_settings, Toast.LENGTH_SHORT).show()
                return@launch
            }
            AlertDialog.Builder(this@SettingsActivity)
                .setTitle(R.string.import_confirm_title)
                .setMessage(R.string.import_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_import) { _, _ ->
                    lifecycleScope.launch { replaceSettings(imported) }
                }
                .show()
        }
    }

    /** Parse the picked file without touching the store. */
    private suspend fun readImportedSettings(uri: Uri): UserPreference? =
        withContext(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    UserPreference.parseFrom(stream)
                }
            } catch (e: InvalidProtocolBufferException) {
                Timber.w(e, "Could not parse settings file for import")
                null
            } catch (e: IOException) {
                Timber.w(e, "Could not read settings file for import")
                null
            } catch (e: SecurityException) {
                // The picker grant can be gone by the time the read runs.
                Timber.w(e, "No permission to read settings file for import")
                null
            }
        }

    /**
     * Replace the whole store, after keeping a copy of what was there.
     *
     * The copy goes to the app files directory, not the cache, so clearing the cache cannot
     * take it. Each copy is staged and swapped in only when complete, so a failed import leaves
     * the previous copy usable. Images are copied first and the store second: a failure leaves
     * the settings untouched instead of half replaced. Everything runs under
     * [BackgroundStore.filesLock] because the dashboard settings sweep deletes background files
     * no setting names, and that sweep would otherwise eat the images the copy refers to. See
     * issue #168.
     */
    private suspend fun replaceSettings(imported: UserPreference) {
        // The user already confirmed the import, so the copy and the replace run to the end even
        // if the screen is rotated away. A cancellation between the two would leave the image
        // copy and the settings copy from different imports.
        val result = withContext(Dispatchers.IO + NonCancellable) {
            try {
                BackgroundStore.filesLock.withLock {
                    copyBackgroundsToBackup()
                    // The transform gets the settings that are still live, and a throw here
                    // aborts the whole write, so a failed copy never replaces anything.
                    applicationContext.dataStore.updateData { current ->
                        writeSettingsBackup(current)
                        imported
                    }
                }
                R.string.import_complete
            } catch (e: IOException) {
                Timber.w(e, "Could not back up settings before import")
                R.string.import_failed
            }
        }
        Toast.makeText(applicationContext, result, Toast.LENGTH_SHORT).show()
    }

    /** Write the live settings aside, then move them over the last copy in one step. */
    private suspend fun writeSettingsBackup(current: UserPreference) {
        val target = File(filesDir, IMPORT_BACKUP_FILE)
        val staged = File(filesDir, "$IMPORT_BACKUP_FILE.tmp")
        try {
            staged.outputStream().use { stream ->
                UserPreferenceSerializer.writeTo(current, stream)
            }
            if (!staged.renameTo(target)) {
                throw IOException("Could not replace $IMPORT_BACKUP_FILE")
            }
        } finally {
            staged.delete()
        }
    }

    /** Keep the background images and their thumbnails with the settings copy. */
    private fun copyBackgroundsToBackup() {
        val source = BackgroundStore.dir(this)
        val target = File(filesDir, IMPORT_BACKUP_DIR)
        val staged = File(filesDir, "$IMPORT_BACKUP_DIR.tmp")
        // A staged directory left by an earlier failure is rebuilt from nothing.
        staged.deleteRecursively()
        if (!staged.mkdirs()) {
            throw IOException("Could not create $IMPORT_BACKUP_DIR.tmp")
        }
        val files = source.listFiles()
        if (files == null && source.exists()) {
            throw IOException("Could not list ${source.name}")
        }
        files?.forEach { file ->
            if (!file.isFile) return@forEach
            file.copyTo(File(staged, file.name), overwrite = true)
        }
        // Swap in the new copy only after it is complete, and keep the old one until then.
        val previous = File(filesDir, "$IMPORT_BACKUP_DIR.old")
        if (target.exists()) {
            previous.deleteRecursively()
            if (!target.renameTo(previous)) {
                throw IOException("Could not set aside $IMPORT_BACKUP_DIR")
            }
        }
        if (!staged.renameTo(target)) {
            // Put the previous copy back rather than leave no backup at all.
            previous.renameTo(target)
            throw IOException("Could not replace $IMPORT_BACKUP_DIR")
        }
        previous.deleteRecursively()
    }

    class ExportFileContract : ActivityResultContract<String, Uri?>() {

        override fun createIntent(context: Context, input: String): Intent =
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = EXPORT_MIME
                // Suggest a file name based on the original file name
                putExtra(Intent.EXTRA_TITLE, "dashboards.pb")
            }

        // This function parses the activity result and returns the URI of the user selected location
        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            if (resultCode == Activity.RESULT_OK) {
                intent?.data
            } else {
                null
            }
    }

    class ImportFileContract : ActivityResultContract<String, Uri?>() {

        override fun createIntent(context: Context, input: String): Intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = input
            }

        // This function parses the activity result and returns the URI of the user selected location
        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            if (resultCode == Activity.RESULT_OK) {
                intent?.data
            } else {
                null
            }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val permissionsToRequest: MutableList<String> = ArrayList()
        if (ContextCompat.checkSelfPermission(this, PERMISSION_CAR_VENDOR_EXTENSION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(PERMISSION_CAR_VENDOR_EXTENSION)
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(permissionsToRequest.toTypedArray(), REQUEST_PERMISSIONS)
            return
        }
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        // Instantiate the new Fragment
        val args = Bundle()
        args.putCharSequence("title", pref.title)
        val prefix = pref.extras.getString("prefix") ?: pref.key
        if (prefix.startsWith("clock_") || prefix.startsWith("display")) {
            val parts = prefix.split("_")
            if (parts.size != 3) return false
            val isClock = parts[0] == "clock"
            val screen = parts[1].toInt()
            val index = parts[2].toInt()
            args.putBoolean("isClock", isClock)
            args.putInt("screen", screen)
            args.putInt("index", index)
        }
        args.putString("prefix", prefix)
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment!!
        )
        fragment.arguments = args
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .replace(R.id.settings_fragment, fragment)
            .addToBackStack(null)
            .commit()
        return true
    }

    private fun updateCheckDue(): Boolean {
        val last = getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
            .getLong(UPDATE_LAST_CHECK, 0L)
        return System.currentTimeMillis() - last > UPDATE_INTERVAL_MS
    }

    private fun markUpdateChecked() {
        getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(UPDATE_LAST_CHECK, System.currentTimeMillis())
            .apply()
    }

    /**
     * Reads the newest published release tag without the GitHub API. The release asset URL
     * redirects to /releases/download/<tag>/aa-torque.apk. The API allows 60 requests per hour
     * per IP address, which is unusable behind a shared NAT or a VPN exit.
     */
    private fun latestReleaseTag(): String? {
        val connection = URL(BuildConfig.RELEASE_URL).openConnection() as HttpsURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = UPDATE_TIMEOUT_MS
        connection.readTimeout = UPDATE_TIMEOUT_MS
        connection.requestMethod = "HEAD"
        return try {
            connection.responseCode
            val location = connection.getHeaderField("Location")
            location?.let { RELEASE_TAG_PATTERN.find(it)?.groupValues?.get(1) }
        } catch (e: IOException) {
            Timber.w(e, "Release lookup failed")
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".")
        val localParts = local.split(".")
        for (position in 0 until maxOf(remoteParts.size, localParts.size)) {
            val remotePart = remoteParts.getOrNull(position)?.toIntOrNull() ?: 0
            val localPart = localParts.getOrNull(position)?.toIntOrNull() ?: 0
            if (remotePart != localPart) return remotePart > localPart
        }
        return false
    }

    /**
     * Automatic checks only tell the user that a release exists and point at it. Installing the
     * APK from inside the app would overwrite the install record Android Auto checks, and the
     * app would vanish from the car. See docs/aa-install-gate.md.
     */
    private fun showUpdateDialog(version: String) {
        val page = "$RELEASES_PAGE/tag/$version"
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            AlertDialog.Builder(this)
                .setTitle(R.string.new_version)
                .setMessage(getString(R.string.update_dialog_message, version))
                .setPositiveButton(R.string.update_open_page) { dialog, _ ->
                    dialog.dismiss()
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page)))
                }
                .setNegativeButton(R.string.update_later) { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    /**
     * @param force skip the 24 h throttle, for the manual "Check for updates" menu entry. Still
     *     only reports; nothing is downloaded or installed.
     */
    private fun checkUpdate(force: Boolean = false): Boolean {
        if (BuildConfig.DEBUG) {
            Timber.i("Skipping update check in debug build")
            return true
        }
        Timber.i("Checking for update")
        if (!force && !updateCheckDue()) {
            Timber.i("Update check is throttled")
            return true
        }
        val latest = latestReleaseTag()
        if (latest == null) {
            Timber.w("Could not read the newest release")
            return false
        }
        markUpdateChecked()

        if (isNewerVersion(latest, BuildConfig.VERSION_NAME)) {
            showUpdateDialog(latest)
        } else {
            Timber.i("No newer release: newest $latest, installed ${BuildConfig.VERSION_NAME}")
            if (force) {
                runOnUiThread {
                    Toast.makeText(this, R.string.no_update_available, Toast.LENGTH_SHORT).show()
                }
            }
        }
        return true
    }

    private fun logsToClipboard() {
        val logs = (application as App).logTree.logToString()
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AA Torque Log", logs.joinToString("\n")))
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 0
        private const val UPDATE_PREFS = "update_check"
        private const val UPDATE_LAST_CHECK = "last_check"
        private const val UPDATE_INTERVAL_MS = 24L * 60L * 60L * 1000L
        private const val RELEASES_PAGE = "https://github.com/YuhiAida/aa-torque/releases"
        private const val UPDATE_TIMEOUT_MS = 5000
        private val RELEASE_TAG_PATTERN = Regex("/releases/download/([^/]+)/")
        private const val PERMISSION_CAR_VENDOR_EXTENSION =
            "com.google.android.gms.permission.CAR_VENDOR_EXTENSION"
        const val EXPORT_MIME = "application/octet-stream"

        /** Where the store is copied before an import replaces it. */
        private const val IMPORT_BACKUP_FILE = "settings-before-import.pb"
        private const val IMPORT_BACKUP_DIR = "settings-before-import-backgrounds"
    }
}
