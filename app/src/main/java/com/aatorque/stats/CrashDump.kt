package com.aatorque.stats

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import timber.log.Timber

/**
 * Writes a fatal exception to a file on the phone. Nothing leaves the device.
 *
 * Android kills the process after the uncaught handler returns, so the dump is written first
 * and the previous handler runs afterwards. A dump lands in
 * `Android/data/<package>/files/crashes`, which a file manager or `adb pull` reads without
 * root; when external storage is not mounted it goes to the private `files/crashes`
 * directory. Only the newest [KEEP_DUMPS] dumps stay.
 */
class CrashDump(private val context: Context, private val logs: CacheLogTree) {

    companion object {
        const val KEEP_DUMPS = 10
        const val DIR_NAME = "crashes"
        const val LOG_LINES = 200
        const val PREFIX = "crash-"
    }

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val file = write(thread, throwable)
                Timber.e("Crash dump written to ${file.absolutePath}")
            } catch (t: Throwable) {
                Timber.e(t, "Could not write the crash dump")
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun dumpDirectory(): File {
        val external = context.getExternalFilesDir(null)
        val base = if (external != null && external.canWrite()) external else context.filesDir
        return File(base, DIR_NAME)
    }

    fun write(thread: Thread, throwable: Throwable): File {
        val dir = dumpDirectory()
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val file = File(dir, "$PREFIX$stamp.txt")
        file.writeText(report(thread, throwable))
        prune(dir)
        return file
    }

    fun report(thread: Thread, throwable: Throwable): String = buildString {
        appendLine("AA Torque crash dump")
        appendLine("time: ${Date()}")
        appendLine("version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("thread: ${thread.name}")
        appendLine()
        appendLine("[stack trace]")
        appendLine(stackTrace(throwable))
        appendLine("[last $LOG_LINES log lines]")
        logs.logToString().takeLast(LOG_LINES).forEach { appendLine(it) }
    }

    private fun stackTrace(throwable: Throwable): String {
        val writer = StringWriter()
        PrintWriter(writer).use { throwable.printStackTrace(it) }
        return writer.toString()
    }

    /** Deletes the oldest dumps so the directory cannot grow without a limit. */
    internal fun prune(dir: File) {
        val dumps = dir.listFiles { file -> file.isFile && file.name.startsWith(PREFIX) }
            ?: return
        dumps.sortedBy { it.name }.dropLast(KEEP_DUMPS).forEach { it.delete() }
    }
}
