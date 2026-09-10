package com.mardous.booming.playback.lyrics

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.mardous.booming.coil.PlaybackArtworkStore

/**
 * Session-facing player that can replace only the current combined metadata.
 *
 * The wrapped player's timeline and MediaItems remain untouched, so clearing the override restores
 * the canonical playback metadata without rebuilding the queue.
 */
@OptIn(UnstableApi::class)
internal class CarLyricsMetadataPlayer(player: Player, placeholder: Uri) : ForwardingSimpleBasePlayer(player) {

    private val overlay = CarLyricsOverlayState(placeholder)

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
