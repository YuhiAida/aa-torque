package com.aatorque.stats

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.IOException
import timber.log.Timber

/**
 * Serves one card bitmap per published frame.
 *
 * Android Auto asks for the art URI it was given in the media metadata, so the frame id in the
 * path is all the provider needs. Rendering happens here, off the service's thread, which keeps
 * the metadata update cheap.
 */
class CardArtworkProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.aatorque.stats.cardart"
        const val FRAME_SIZE = 512
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val id = uri.lastPathSegment?.toIntOrNull()
        val snapshot = id?.let { CardFrames.snapshot(it) }
        if (snapshot == null) {
            Timber.w("Card frame $id not found ($uri)")
            throw IOException("unknown card frame $id")
        }
        val bitmap = CardArtwork.render(
            requireNotNull(context),
            snapshot.theme,
            snapshot.style,
            snapshot.metrics,
            FRAME_SIZE
        )
        val pipe = ParcelFileDescriptor.createPipe()
        Thread({
            ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            bitmap.recycle()
        }, "card-frame-writer").start()
        return pipe[0]
    }

    override fun getType(uri: Uri): String = "image/png"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
