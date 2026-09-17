package com.mardous.booming.playback.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarArtworkModeTest {
    @Test
    fun `lyrics can be enabled from the normal cover`() {
        val mode = CarArtworkMode.COVER.toggle(CarArtworkMode.LYRICS)
        assertTrue(mode.isEnabled)
        assertTrue(mode.artworkEnabled)
    }

    @Test
    fun `pressing the active button restores the regular cover`() {
        assertEquals(CarArtworkMode.COVER, CarArtworkMode.LYRICS.toggle(CarArtworkMode.LYRICS))
        assertFalse(CarArtworkMode.COVER.isEnabled)
    }

    @Test
    fun `queue artwork is no longer an available mode`() {
        assertEquals(listOf(CarArtworkMode.COVER, CarArtworkMode.LYRICS), CarArtworkMode.entries)
    }
}
