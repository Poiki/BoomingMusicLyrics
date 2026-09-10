package com.mardous.booming.playback.lyrics

import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CarLyricsMetadataTest {
    @Test
    fun `no artwork override preserves every original text field and metadata identity`() {
        val original = MediaMetadata.Builder()
            .setTitle("Song title")
            .setDisplayTitle("Display title")
            .setArtist("Artist")
            .setAlbumTitle("Album")
            .setSubtitle("Original subtitle")
            .build()
        val actual = original.withCarLyricsOverlay(CarLyricsOverlayState.Resolved(null))
        assertSame(original, actual)
        assertEquals("Display title", actual.displayTitle)
        assertEquals("Original subtitle", actual.subtitle)
    }

    @Test
    fun `embedded artwork without a provider URI is preserved`() {
        val bytes = byteArrayOf(1, 2, 3)
        val original = MediaMetadata.Builder()
            .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .build()
        val actual = original.withCarLyricsOverlay(CarLyricsOverlayState.Resolved(null))
        assertArrayEquals(bytes, actual.artworkData)
    }
}
