package com.mardous.booming.coil

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import com.mardous.booming.BuildConfig
import java.io.ByteArrayOutputStream

/** Shares ready-to-display covers and their encoded bytes with the session and the car's URI loader. */
internal object PlaybackArtworkStore {
    const val IMAGE_SIZE = 512
    const val JPEG_QUALITY = 85
    private const val PLAYBACK_PARAM = "playback"
    private val processToken = System.nanoTime().toString(36)
    private var revision = 0L
    private val entries = ArtworkMemoryCache<Uri, Artwork>(8 * 1024 * 1024) {
        it.bitmap.allocationByteCount + it.bytes.size
    }

    data class Artwork(val bitmap: Bitmap, val bytes: ByteArray)

    @Synchronized
    fun uriFor(source: Uri?): Uri? {
        if (source == null || !isSongCover(source)) return source
        val builder = source.buildUpon().clearQuery()
        source.queryParameterNames.filter { it != PLAYBACK_PARAM }.forEach { key ->
            source.getQueryParameters(key).forEach { builder.appendQueryParameter(key, it) }
        }
        return builder.appendQueryParameter(PLAYBACK_PARAM, "$processToken-$revision").build()
    }

    fun isPlaybackArtwork(uri: Uri): Boolean =
        isSongCover(uri) && uri.getQueryParameter(PLAYBACK_PARAM) != null

    private fun isSongCover(uri: Uri): Boolean =
        uri.scheme == ContentResolver.SCHEME_CONTENT &&
                uri.authority == "${BuildConfig.APPLICATION_ID}.cover" &&
                uri.pathSegments.firstOrNull() == CoverProvider.SONG_COVER_PATH

    fun get(uri: Uri): Artwork? = entries.get(uri)

    @Synchronized
    fun put(uri: Uri, artwork: Artwork) {
        if (uri.getQueryParameter(PLAYBACK_PARAM) == "$processToken-$revision") {
            entries.put(uri, artwork)
        }
    }

    @Synchronized
    fun invalidate() {
        revision++
        entries.clear()
    }

    fun encode(source: Bitmap): Artwork {
        val scale = minOf(1f, IMAGE_SIZE.toFloat() / maxOf(source.width, source.height))
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else source
        val bytes = ByteArrayOutputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it))
            it.toByteArray()
        }
        return Artwork(bitmap, bytes)
    }
}
