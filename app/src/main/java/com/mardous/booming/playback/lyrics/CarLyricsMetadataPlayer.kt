package com.mardous.booming.playback.lyrics

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.mardous.booming.coil.PlaybackArtworkStore
import com.mardous.booming.playback.library.CarQueueBrowser
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Session-facing player that can replace only the current combined metadata.
 *
 * The wrapped player's timeline and MediaItems remain untouched, so clearing the override restores
 * the canonical playback metadata without rebuilding the queue.
 */
@OptIn(UnstableApi::class)
internal class CarLyricsMetadataPlayer(player: Player, placeholder: Uri) : ForwardingSimpleBasePlayer(player) {

    private val overlay = CarLyricsOverlayState(placeholder)
    var selectQueueItem: ((String, Long) -> Boolean)? = null

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long
    ): ListenableFuture<*> {
        if (mediaItems.any { CarQueueBrowser.isQueueItem(it.mediaId) }) {
            // Legacy playFromMediaId always calls setMediaItems. Seek before it can replace the
            // playlist, preserving duplicate entries, shuffle order, and preloaded media sources.
            return if (mediaItems.size == 1 &&
                selectQueueItem?.invoke(mediaItems.single().mediaId, startPositionMs) == true) {
                Futures.immediateVoidFuture()
            } else {
                Futures.immediateFailedFuture<Void>(IllegalArgumentException("Queue item is no longer available"))
            }
        }
        return super.handleSetMediaItems(mediaItems, startIndex, startPositionMs)
    }

    val sourceMetadata: MediaMetadata
        get() = if (getPlayer().isCommandAvailable(Player.COMMAND_GET_METADATA)) {
            getPlayer().mediaMetadata
        } else {
            MediaMetadata.EMPTY
        }

    override fun getState(): State {
        val sourceState = super.getState()
        val override = overlay.resolve(getPlayer().currentMediaItem?.mediaId)
        val source = sourceMetadata
        val metadata = source.withCarLyricsOverlay(override)
        if (metadata === source) return sourceState
        return sourceState.buildUpon()
            .setPlaylist(sourceState.timeline, sourceState.currentTracks, metadata)
            .build()
    }

    fun setModes(modes: CarArtworkMode) {
        if (overlay.modes == modes) return
        overlay.modes = modes
        invalidateState()
    }

    fun setMetadataOverride(mediaId: String, artworkUri: Uri?) {
        val previous = overlay.resolve(getPlayer().currentMediaItem?.mediaId)
        overlay.publish(mediaId, artworkUri)
        if (previous == overlay.resolve(getPlayer().currentMediaItem?.mediaId)) return
        invalidateState()
    }

    fun clearMetadataOverride() {
        overlay.clear()
        invalidateState()
    }

    fun refreshArtwork() = invalidateState()
}

internal fun MediaMetadata.withCarLyricsOverlay(
    overlay: CarLyricsOverlayState.Resolved<Uri>
): MediaMetadata {
    val hasEmbeddedArtwork = artworkUri != null && artworkData != null
    val playbackArtworkUri = PlaybackArtworkStore.uriFor(artworkUri)
    if (overlay.artwork == null && !hasEmbeddedArtwork &&
        playbackArtworkUri == artworkUri) return this
    return buildUpon().apply {
        setArtworkUri(playbackArtworkUri)
        if (hasEmbeddedArtwork) setArtworkData(null, null)
        overlay.artwork?.let {
            setArtworkData(null, null)
            setArtworkUri(it)
        }
    }.build()
}
