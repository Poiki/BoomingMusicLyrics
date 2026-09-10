package com.mardous.booming.playback.lyrics

/** Resolves overlays against the current song even before transition listeners have run. */
internal class CarLyricsOverlayState<Artwork>(private val placeholder: Artwork) {
    var modes = CarArtworkMode.COVER
        set(value) {
            if (field == value) return
            field = value
            clear()
        }
    private var mediaId: String? = null
    private var artwork: Artwork? = null

    fun publish(mediaId: String, artwork: Artwork?) {
        this.mediaId = mediaId
        this.artwork = artwork
    }

    fun resolve(currentMediaId: String?): Resolved<Artwork> {
        if (currentMediaId == null) return Resolved(null)
        val matches = currentMediaId == mediaId
        return Resolved(
            artwork = if (modes.isEnabled) {
                artwork.takeIf { matches } ?: placeholder
            } else null
        )
    }

    fun clear() {
        mediaId = null
        artwork = null
    }

    data class Resolved<Artwork>(val artwork: Artwork?)
}
