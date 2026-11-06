package com.aatorque.prefs

import android.content.Context
import android.graphics.Matrix
import android.media.ExifInterface
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

        val a = "bg-aaaaaaaa.jpg"
        val b = "bg-bbbbbbbb.jpg"
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

        val older = File(dir, "bg-cccccccc.jpg")
        val newer = File(dir, "bg-dddddddd.jpg")
        older.writeBytes(byteArrayOf(1))
        // ensure mtime difference
        older.setLastModified(1000L)
        newer.writeBytes(byteArrayOf(2))
        newer.setLastModified(2000L)

        val names = BackgroundStore.list(context)
        assertEquals(listOf("bg-dddddddd.jpg", "bg-cccccccc.jpg"), names)
    }

    @Test
    fun listExcludesThumbnails() {
        val dir = BackgroundStore.dir(context)
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        File(dir, "bg-eeeeeeee.jpg").writeBytes(byteArrayOf(1))
        File(dir, "bg-eeeeeeee.jpg.thumb.jpg").writeBytes(byteArrayOf(2))
        val names = BackgroundStore.list(context)
        assertEquals(listOf("bg-eeeeeeee.jpg"), names)
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

    @Test
    fun sweepLeavesNonMatchingStaleFileAlone() {
        val dir = BackgroundStore.dir(context)
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        File(dir, "notes.jpg").writeBytes(byteArrayOf(1))
        val deleted = BackgroundStore.sweep(context, emptySet())
        assertEquals(0, deleted)
        assertTrue(File(dir, "notes.jpg").exists())
    }

    @Test
    fun listExcludesNonMatchingJpg() {
        val dir = BackgroundStore.dir(context)
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        File(dir, "notes.jpg").writeBytes(byteArrayOf(1))
        File(dir, "bg-ffffffff.jpg").writeBytes(byteArrayOf(2))
        val names = BackgroundStore.list(context)
        assertEquals(listOf("bg-ffffffff.jpg"), names)
    }

    @Test
    fun orientationMatrixReturnsMatrixForAllEightCodes() {
        // EXIF orientations 2..8 (all except NORMAL) must yield a matrix.
        for (code in 2..8) {
            assertTrue(BackgroundStore.orientationMatrix(code, 1600, 900) != null)
        }
    }

    @Test
    fun orientationMatrixNullForNormalAndUndefined() {
        assertEquals(
            null,
            BackgroundStore.orientationMatrix(ExifInterface.ORIENTATION_NORMAL, 1600, 900)
        )
        assertEquals(
            null,
            BackgroundStore.orientationMatrix(ExifInterface.ORIENTATION_UNDEFINED, 1600, 900)
        )
    }

    @Test
    fun orientationMatrixMaps90() {
        val m = BackgroundStore.orientationMatrix(ExifInterface.ORIENTATION_ROTATE_90, 1600, 900)!!
        val pts = floatArrayOf(1600f, 900f)
        m.mapPoints(pts)
        assertEquals(-900f, pts[0], 0.5f)
        assertEquals(1600f, pts[1], 0.5f)
    }
}
