package com.aatorque.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackgroundStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun newNameMatchesPattern() {
        val name = BackgroundStore.newName()
        assertTrue(name.matches(Regex("bg-[0-9a-f]{8}\\.jpg")))
    }

    @Test
    fun sweepDeletesOnlyUnreferencedImages() {
        val dir = BackgroundStore.dir(context)
        dir.mkdirs()
        // clean
        dir.listFiles()?.forEach { it.delete() }

        val keep = "bg-11111111.jpg"
        val orphan = "bg-22222222.jpg"
        File(dir, keep).writeBytes(byteArrayOf(1, 2, 3))
        File(dir, orphan).writeBytes(byteArrayOf(4, 5, 6))
        // thumbnail of orphan
        File(dir, "bg-22222222.jpg.thumb.jpg").writeBytes(byteArrayOf(7))

        val deleted = BackgroundStore.sweep(context, setOf(keep))

        assertEquals(1, deleted)
        assertTrue(File(dir, keep).exists())
        assertFalse(File(dir, "bg-22222222.jpg").exists())
        assertFalse(File(dir, "bg-22222222.jpg.thumb.jpg").exists())
    }

    @Test
    fun sweepNeverDeletesReferenced() {
        val dir = BackgroundStore.dir(context)
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }

        val a = "a.jpg"
        val b = "b.jpg"
        File(dir, a).writeBytes(byteArrayOf(1))
        File(dir, b).writeBytes(byteArrayOf(2))

        val deleted = BackgroundStore.sweep(context, setOf(a, b))
        assertEquals(0, deleted)
        assertTrue(File(dir, a).exists())
        assertTrue(File(dir, b).exists())
    }

    @Test
    fun listReturnsNewestFirst() {
        val dir = BackgroundStore.dir(context)
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }

        val older = File(dir, "older.jpg")
        val newer = File(dir, "newer.jpg")
        older.writeBytes(byteArrayOf(1))
        // ensure mtime difference
        older.setLastModified(1000L)
        newer.writeBytes(byteArrayOf(2))
        newer.setLastModified(2000L)

        val names = BackgroundStore.list(context)
        assertEquals(listOf("newer.jpg", "older.jpg"), names)
    }

    @Test
    fun listExcludesThumbnails() {
        val dir = BackgroundStore.dir(context)
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        File(dir, "img.jpg").writeBytes(byteArrayOf(1))
        File(dir, "img.jpg.thumb.jpg").writeBytes(byteArrayOf(2))
        val names = BackgroundStore.list(context)
        assertEquals(listOf("img.jpg"), names)
    }

    @Test
    fun resolveMissingReturnsNull() {
        val result = BackgroundStore.resolve(context, "does-not-exist.jpg", 100)
        assertEquals(null, result)
    }

    @Test
    fun resolveRejectsUnsafeNames() {
        assertEquals(null, BackgroundStore.resolve(context, "../evil.jpg", 100))
        assertEquals(null, BackgroundStore.resolve(context, "evil.jpg", 100))
    }

    @Test
    fun thumbnailRejectsUnsafeNames() {
        assertEquals(null, BackgroundStore.thumbnail(context, "../evil.jpg"))
        assertEquals(null, BackgroundStore.thumbnail(context, "evil.jpg"))
    }

    @Test
    fun thumbnailMissingReturnsNull() {
        val result = BackgroundStore.thumbnail(context, "does-not-exist.jpg")
        assertEquals(null, result)
    }

    @Test
    fun sweepLeavesOutsideFilesAlone() {
        val dir = BackgroundStore.dir(context)
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val outside = File(dir.parentFile, "evil.jpg")
        outside.writeBytes(byteArrayOf(9))
        try {
            BackgroundStore.sweep(context, emptySet())
            assertTrue(outside.exists())
        } finally {
            outside.delete()
        }
    }
}
