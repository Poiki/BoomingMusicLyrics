package com.mardous.booming.playback.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarArtworkModeTest {
    @Test
    fun `queue replaces lyrics and does not keep lyrics updates enabled`() {
        val mode = CarArtworkMode.LYRICS.toggle(CarArtworkMode.QUEUE)
        assertTrue(mode.isEnabled)
        assertTrue(mode.queueEnabled)
        assertFalse(mode.artworkEnabled)
    }

    @Test
    fun `pressing the active button restores the regular cover`() {
        assertEquals(CarArtworkMode.COVER, CarArtworkMode.QUEUE.toggle(CarArtworkMode.QUEUE))
        assertEquals(CarArtworkMode.COVER, CarArtworkMode.LYRICS.toggle(CarArtworkMode.LYRICS))
        assertFalse(CarArtworkMode.COVER.isEnabled)
    }

    @Test
    fun `lyrics replace the queue without a third active mode`() {
        val mode = CarArtworkMode.QUEUE.toggle(CarArtworkMode.LYRICS)
        assertTrue(mode.artworkEnabled)
        assertFalse(mode.queueEnabled)
    }
}
