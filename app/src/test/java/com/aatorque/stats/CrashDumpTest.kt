package com.aatorque.stats

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A crash dump must end up on the phone and hold the parts a maintainer reads: the stack
 * trace, the build, and the log tail. Nothing here exercises a real crash — that kills the
 * unit — so the report and the file are tested directly.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CrashDumpTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dump = CrashDump(context, CacheLogTree())

    @Test
    fun reportHoldsStackAndBuild() {
        val thread = Thread.currentThread()
        val text = dump.report(thread, IllegalStateException("boom"))
        assertTrue(text.contains("java.lang.IllegalStateException: boom"))
        assertTrue(text.contains("version: ${BuildConfig.VERSION_NAME}"))
        assertTrue(text.contains("thread: ${thread.name}"))
    }

    @Test
    fun writeLandsInTheDumpDirectory() {
        val file = dump.write(Thread.currentThread(), IllegalStateException("boom"))
        assertTrue(file.exists())
        assertEquals(dump.dumpDirectory(), file.parentFile)
        assertTrue(file.readText().contains("java.lang.IllegalStateException: boom"))
    }

    @Test
    fun pruneKeepsTheNewestDumps() {
        val dir = File(context.filesDir, "prune-test").apply { mkdirs() }
        for (n in 1..15) {
            File(dir, "crash-${n.toString().padStart(4, '0')}.txt").writeText("dump $n")
        }
        dump.prune(dir)
        val names = dir.listFiles()!!.map { it.name }.sorted()
        assertEquals(CrashDump.KEEP_DUMPS, names.size)
        assertEquals("crash-0006.txt", names.first())
        dir.deleteRecursively()
    }
}
