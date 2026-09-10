package com.mardous.booming.playback.lyrics

internal data class CarLyricsPublicationKey(
    val mediaId: String,
    val primary: String,
    val secondary: String?,
    val modes: CarArtworkMode
)

internal class CarLyricsPublicationGate {

    var current: CarLyricsPublicationKey? = null
        private set

    fun shouldPublish(key: CarLyricsPublicationKey): Boolean {
        if (current == key) return false
        current = key
        return true
    }

    fun reset() {
        current = null
    }
}
