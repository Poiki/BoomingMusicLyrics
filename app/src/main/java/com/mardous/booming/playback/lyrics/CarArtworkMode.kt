package com.mardous.booming.playback.lyrics

internal enum class CarArtworkMode {
    COVER, LYRICS, QUEUE;

    val artworkEnabled: Boolean
        get() = this == LYRICS
    val queueEnabled: Boolean
        get() = this == QUEUE
    val isEnabled: Boolean
        get() = this != COVER

    fun toggle(target: CarArtworkMode): CarArtworkMode = if (this == target) COVER else target
}

internal data class CarLyricsTextOverride(
    val primary: String,
    val secondary: String?
)
