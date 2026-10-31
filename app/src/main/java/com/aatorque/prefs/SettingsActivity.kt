package com.aatorque.prefs

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.isDigitsOnly
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
import com.google.android.material.snackbar.Snackbar
import com.google.protobuf.InvalidProtocolBufferException
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class SettingsActivity :
    AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    val br: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.`package` == BuildConfig.APPLICATION_ID) {
                AlertDialog.Builder(this@SettingsActivity).setTitle(R.string.download_complete)
                    .setMessage(R.string.download_dialog)
                    .setPositiveButton(
                        android.R.string.ok,
                        object : DialogInterface.OnClickListener {
                            override fun onClick(p0: DialogInterface?, p1: Int) {
                                val downloadId = intent.getLongExtra(
                                    DownloadManager.EXTRA_DOWNLOAD_ID,
                                    -1L
                                )
                                if (downloadId == -1L) return
                                val uri = (
                                    getSystemService(
                                        DOWNLOAD_SERVICE
                                    ) as DownloadManager
                                    ).getUriForDownloadedFile(downloadId)
                                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    Intent(Intent.ACTION_INSTALL_PACKAGE).also {
                                        it.data = uri
                                        it.flags =
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                            Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                } else {
                                    Intent(Intent.ACTION_VIEW).also {
                                        it.setDataAndTypeAndNormalize(
                                            uri,
                                            "application/vnd.android.package-archive"
                                        )
                                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                }
                                intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                                intent.putExtra(
                                    Intent.EXTRA_INSTALLER_PACKAGE_NAME,
                                    "com.android.vending"
                                )
                                applicationContext.startActivity(intent)
                            }
                        }
                    ).setNegativeButton(android.R.string.cancel) { p0, p1 -> p0?.dismiss() }.show()
            }
        }
    }

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

                        is SettingsPIDFragment -> {
                            f.requireArguments().getCharSequence("title")
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

            R.id.force_update -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    if (!checkUpdate(true)) {
                        runOnUiThread {
                            Toast.makeText(
                                this@SettingsActivity,
                                R.string.update_failed,
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
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    UserPreferenceSerializer.writeTo(data, outputStream)
                }
                R.string.file_exported_successfully
            } else {
                R.string.export_failed
            }
            runOnUiThread {
                Toast.makeText(baseContext, result, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importFileLauncher = registerForActivityResult(ImportFileContract()) { uri: Uri? ->
        if (uri != null) {
            val inStream = contentResolver.openInputStream(uri)
            this@SettingsActivity.lifecycleScope.launch {
                try {
                    this@SettingsActivity.applicationContext.dataStore.updateData {
                        return@updateData UserPreference.parseFrom(inStream)
                    }
                } catch (e: InvalidProtocolBufferException) {
                    Toast.makeText(
                        baseContext,
                        R.string.invalid_settings,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
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
            assert(parts.size == 3)
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

    private fun checkUpdate(force: Boolean = false): Boolean {
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

        if (!force) {
            if (isNewerVersion(latest, BuildConfig.VERSION_NAME)) {
                showUpdateDialog(latest)
            } else {
                Timber.i("No newer release: newest $latest, installed ${BuildConfig.VERSION_NAME}")
            }
            return true
        }

        Snackbar.make(
            findViewById(android.R.id.content),
            R.string.new_version,
            Snackbar.LENGTH_LONG
        )
            .setDuration(10000)
            .setActionTextColor(resources.getColor(R.color.snackbarBtnText, theme))
            .setAction(R.string.download) {
                ContextCompat.registerReceiver(
                    baseContext,
                    br,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    ContextCompat.RECEIVER_EXPORTED
                )
                val downloadRequest = DownloadManager.Request(Uri.parse(BuildConfig.RELEASE_URL))
                downloadRequest.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "aa-torque.apk"
                )
                downloadRequest.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(downloadRequest)

                Toast.makeText(
                    baseContext,
                    R.string.download_instructions,
                    Toast.LENGTH_SHORT
                ).show()
            }.show()
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
    }
}
