package com.aatorque.prefs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * File-backed store for user-imported background images.
 *
 * Every function here that touches disk or decodes a bitmap is a plain blocking
 * call; callers are responsible for running them on [kotlinx.coroutines.Dispatchers.IO].
 */
object BackgroundStore {

    private const val DIR_NAME = "backgrounds"
    private const val THUMB_SUFFIX = ".thumb.jpg"
    private const val MAX_READ_BYTES = 64 * 1024 * 1024

    fun dir(context: Context): File = File(context.filesDir, DIR_NAME)

    fun list(context: Context): List<String> {
        val directory = dir(context)
        val files = directory.listFiles() ?: return emptyList()
        return files
            .filter { it.isFile && it.name.endsWith(".jpg") && !it.name.endsWith(THUMB_SUFFIX) }
            .sortedByDescending { it.lastModified() }
            .map { it.name }
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
                if (base in referenced) continue
                file.delete()
            } else if (name.endsWith(".jpg")) {
                if (name in referenced) continue
                if (file.delete()) deletedImages++
                File(directory, "$name$THUMB_SUFFIX").delete()
            }
        }
        return deletedImages
    }

    fun newName(): String = "bg-${UUID.randomUUID().toString().replace("-", "").take(8)}.jpg"

    fun import(context: Context, uri: android.net.Uri, maxEdge: Int = 2048): String? {
        val resolver = context.contentResolver
        val bytes = try {
            resolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArrayOutputStream()
                val tmp = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(tmp)
                    if (read < 0) break
                    if (total + read > MAX_READ_BYTES) return null
                    buffer.write(tmp, 0, read)
                    total += read
                }
                buffer.toByteArray()
            } ?: return null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return null
        }
        if (bytes.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val mime = bounds.outMimeType
        if (mime == null || !mime.startsWith("image/")) return null

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        val decoded = try {
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        } ?: return null

        val longEdge = maxOf(decoded.width, decoded.height)
        val scaled = if (longEdge > maxEdge) {
            val ratio = maxEdge.toFloat() / longEdge
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * ratio).toInt().coerceAtLeast(1),
                (decoded.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            decoded
        }

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

        val directory = dir(context)
        directory.mkdirs()
        val name = newName()
        val imageFile = File(directory, name)
        val thumbFile = File(directory, "$name$THUMB_SUFFIX")

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
        return name
    }

    fun resolve(context: Context, name: String, reqHeight: Int): Bitmap? {
        val file = File(dir(context), name)
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
        val file = File(dir(context), "$name$THUMB_SUFFIX")
        if (!file.isFile) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
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
