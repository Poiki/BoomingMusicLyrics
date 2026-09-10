package com.mardous.booming.playback.lyrics

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.mardous.booming.R
import java.io.FileNotFoundException
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong

/** Serves revisioned in-memory artwork so car hosts cannot reuse a previous lyric line from cache. */
class CarLyricsArtworkProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read-only provider")
        val data = CarLyricsArtworkStore.read(uri) ?: throw FileNotFoundException(uri.toString())
        return openPipeHelper(uri, MIME_TYPE, null, data) { output, _, _, _, bytes ->
            ParcelFileDescriptor.AutoCloseOutputStream(output).use { it.write(bytes) }
        }
    }

    override fun getType(uri: Uri): String = MIME_TYPE

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val data = CarLyricsArtworkStore.read(uri) ?: return null
        val requestedColumns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(requestedColumns, 1).apply {
            addRow(requestedColumns.map { column ->
                when (column) {
                    OpenableColumns.DISPLAY_NAME -> uri.lastPathSegment
                    OpenableColumns.SIZE -> data.size
                    else -> null
                }
            })
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private companion object {
        const val MIME_TYPE = "image/png"
    }
}

internal object CarLyricsArtworkStore {
    private const val AUTHORITY_SUFFIX = ".carlyricsartwork"
    private const val MAX_ENTRIES = 8
    private val processToken = System.nanoTime().toString(36)
    private val publicationRevision = AtomicLong()
    private val entries = LinkedHashMap<String, ByteArray>(MAX_ENTRIES, 0.75f, true)
    private val bitmaps = LinkedHashMap<String, Bitmap>()

    fun placeholderUri(context: Context): Uri = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.packageName)
        .appendPath(R.drawable.car_lyrics_placeholder.toString())
        .build()

    fun publish(
        context: Context,
        songId: Long?,
        generation: Long,
        artwork: CarLyricsArtwork
    ): Uri {
        val key = "$processToken-${songId ?: "none"}-$generation-${publicationRevision.incrementAndGet()}.png"
        synchronized(entries) {
            entries[key] = artwork.data
            bitmaps[key] = artwork.bitmap
            while (bitmaps.size > 2) {
                bitmaps.remove(bitmaps.entries.first().key)
            }
            while (entries.size > MAX_ENTRIES) {
                entries.remove(entries.entries.first().key)
            }
        }
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(context.packageName + AUTHORITY_SUFFIX)
            .appendPath(key)
            .build()
    }

    fun read(uri: Uri): ByteArray? {
        val key = uri.lastPathSegment ?: return null
        return synchronized(entries) { entries[key] }
    }

    fun readBitmap(uri: Uri): Bitmap? = synchronized(entries) {
        bitmaps[uri.lastPathSegment]
    }

    fun clear() {
        synchronized(entries) {
            entries.clear()
            bitmaps.clear()
        }
    }
}
