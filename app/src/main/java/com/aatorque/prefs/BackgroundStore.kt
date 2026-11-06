package com.aatorque.prefs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.sync.Mutex

/**
 * File-backed store for user-imported background images.
 *
 * Every function here that touches disk or decodes a bitmap is a plain blocking
 * call; callers are responsible for running them on [kotlinx.coroutines.Dispatchers.IO].
 */
object BackgroundStore {

    /**
     * Guards every import, reference write and sweep together. A sweep that runs between an
     * import and the update that references the new file deletes that file, and a settings
     * import can add a reference the sweep has not read yet, so all three take this first.
     */
    val filesLock = Mutex()

    private const val DIR_NAME = "backgrounds"
    private const val THUMB_SUFFIX = ".thumb.jpg"

    fun dir(context: Context): File = File(context.filesDir, DIR_NAME)

    fun list(context: Context): List<String> {
        val directory = dir(context)
        val files = directory.listFiles() ?: return emptyList()
        return files
            .filter {
                it.isFile &&
                    it.name.endsWith(".jpg") &&
                    !it.name.endsWith(THUMB_SUFFIX) &&
                    it.name.matches(SAFE_NAME)
            }
            .sortedByDescending { it.lastModified() }
            .map { it.name }
    }

    private val SAFE_NAME = Regex("^bg-[0-9a-f]{8}\\.jpg$")

    private fun fileFor(context: Context, name: String): File? {
        if (!name.matches(SAFE_NAME)) return null
        return File(dir(context), name)
    }

    fun sweep(context: Context, referenced: Set<String>): Int {
        val directory = dir(context)
        val files = directory.listFiles() ?: return 0
        var deletedImages = 0
        for (file in files) {
            if (!file.isFile) continue
            val name = file.name
            if (name.endsWith(THUMB_SUFFIX)) {
                val base = name.removeSuffix(THUMB_SUFFIX)
                if (!base.matches(SAFE_NAME)) continue
                if (base in referenced) continue
                file.delete()
            } else if (name.matches(SAFE_NAME)) {
                if (name in referenced) continue
                if (file.delete()) deletedImages++
                File(directory, "$name$THUMB_SUFFIX").delete()
            }
        }
        return deletedImages
    }

    fun newName(): String = "bg-${UUID.randomUUID().toString().replace("-", "").take(8)}.jpg"

    fun import(context: Context, uri: android.net.Uri, maxEdge: Int = 2048): String? {
        val tmpFile = try {
            File.createTempFile("bg-import", ".jpg", context.cacheDir)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return null
        }

        var imageFile: File? = null
        var thumbFile: File? = null
        return try {
            tmpFile.outputStream().use { out ->
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.copyTo(out, 64 * 1024)
                } ?: return null
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmpFile.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bitmap = BitmapFactory.decodeFile(tmpFile.absolutePath, opts) ?: return null

            val orientation = try {
                ExifInterface(tmpFile.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ExifInterface.ORIENTATION_NORMAL
            }

            val matrix = orientationMatrix(orientation, bitmap.width, bitmap.height)
            if (matrix != null) {
                val rotated = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                    matrix,
                    true
                )
                if (rotated !== bitmap) bitmap.recycle()
                bitmap = rotated
            }

            val scaled = if (maxOf(bitmap.width, bitmap.height) > maxEdge) {
                val ratio = maxEdge.toFloat() / maxOf(bitmap.width, bitmap.height)
                val nb = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt().coerceAtLeast(1),
                    (bitmap.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
                if (nb !== bitmap) bitmap.recycle()
                nb
            } else {
                bitmap
            }

            val directory = dir(context)
            directory.mkdirs()
            val name = newName()
            imageFile = File(directory, name)
            thumbFile = File(directory, "$name$THUMB_SUFFIX")

            val thumbLong = 128
            val thumbEdge = maxOf(scaled.width, scaled.height)
            val thumb = if (thumbEdge > thumbLong) {
                val ratio = thumbLong.toFloat() / thumbEdge
                Bitmap.createScaledBitmap(
                    scaled,
                    (scaled.width * ratio).toInt().coerceAtLeast(1),
                    (scaled.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                scaled
            }

            var ok = true
            try {
                FileOutputStream(imageFile).use { out ->
                    if (!scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)) ok = false
                }
                if (ok) {
                    FileOutputStream(thumbFile).use { out ->
                        if (!thumb.compress(Bitmap.CompressFormat.JPEG, 90, out)) ok = false
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ok = false
            }
            if (!ok) {
                imageFile.delete()
                thumbFile.delete()
                return null
            }
            name
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            imageFile?.delete()
            thumbFile?.delete()
            null
        } finally {
            tmpFile.delete()
        }
    }

    internal fun orientationMatrix(orientation: Int, width: Int, height: Int): Matrix? {
        val matrix = Matrix()
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                matrix.postRotate(90f)
                matrix
            }

            ExifInterface.ORIENTATION_ROTATE_180 -> {
                matrix.postRotate(180f)
                matrix
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> {
                matrix.postRotate(270f)
                matrix
            }

            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                matrix.postScale(-1f, 1f)
                matrix
            }

            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.postScale(1f, -1f)
                matrix
            }

            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
                matrix
            }

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
                matrix
            }

            else -> null
        }
    }

    fun resolve(context: Context, name: String, reqHeight: Int): Bitmap? {
        val file = fileFor(context, name) ?: return null
        if (!file.isFile) return null
        return try {
            if (reqHeight <= 0) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                val sample = calculateInSampleSize(
                    bounds.outWidth,
                    bounds.outHeight,
                    reqHeight,
                    byHeight = true
                )
                BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                )
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    fun thumbnail(context: Context, name: String): Bitmap? {
        fileFor(context, name) ?: return null
        val file = File(dir(context), "$name$THUMB_SUFFIX")
        if (!file.isFile) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        target: Int,
        byHeight: Boolean = false
    ): Int {
        var sample = 1
        val source = if (byHeight) height else maxOf(width, height)
        while (source / sample > target) {
            sample *= 2
        }
        return sample
    }
}
