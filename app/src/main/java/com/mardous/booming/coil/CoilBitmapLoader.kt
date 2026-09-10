package com.mardous.booming.coil

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mardous.booming.R
import com.mardous.booming.playback.lyrics.CarLyricsArtworkStore

@UnstableApi
class CoilBitmapLoader(
    private val context: Context
) : BitmapLoader {

    private val lyricsPlaceholderUri = CarLyricsArtworkStore.placeholderUri(context)
    private val lyricsPlaceholder by lazy {
        checkNotNull(ContextCompat.getDrawable(context, R.drawable.car_lyrics_placeholder))
            .toBitmap(MAX_BITMAP_SIZE, MAX_BITMAP_SIZE)
    }

    override fun supportsMimeType(mimeType: String): Boolean {
        return Util.isBitmapFactorySupportedMimeType(mimeType)
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        return loadImageUsingData(data)
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        // Avoid a PNG encode/decode round trip and a second session update for every lyric line.
        CarLyricsArtworkStore.readBitmap(uri)?.let { return Futures.immediateFuture(it) }
        if (uri == lyricsPlaceholderUri) return Futures.immediateFuture(lyricsPlaceholder)
        PlaybackArtworkStore.get(uri)?.let { return Futures.immediateFuture(it.bitmap) }
        return loadImageUsingData(uri)
    }

    private fun loadImageUsingData(data: Any): ListenableFuture<Bitmap> {
        return CallbackToFutureAdapter.getFuture { completer ->
            SingletonImageLoader.get(context).enqueue(
                ImageRequest.Builder(context)
                    .data(data)
                    .target(
                        onError = {
                            completer.setException(Exception("Coil failed to load the image"))
                        },
                        onSuccess = {
                            val readyArtwork = (data as? Uri)?.let(PlaybackArtworkStore::get)
                            completer.set(readyArtwork?.bitmap ?: it.toBitmap())
                        }
                    )
                    .memoryCachePolicy(
                        if (data is Uri && PlaybackArtworkStore.isPlaybackArtwork(data)) {
                            CachePolicy.DISABLED
                        } else CachePolicy.ENABLED
                    )
                    .allowHardware(false)
                    .size(MAX_BITMAP_SIZE)
                    .build()
            ).also {
                completer.addCancellationListener(
                    { it.dispose() },
                    ContextCompat.getMainExecutor(context)
                )
            }
        }
    }

    companion object {
        private const val MAX_BITMAP_SIZE = 640
    }
}
