package com.mardous.booming.playback.lyrics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarLyricsPublicationGateTest {

    @Test
    fun `same song line and modes publish only once`() {
        val gate = CarLyricsPublicationGate()
        val key = key(mediaId = "1", primary = "Current")

        assertTrue(gate.shouldPublish(key))
        assertFalse(gate.shouldPublish(key))
    }

    @Test
    fun `song line and mode changes each trigger publication`() {
        val gate = CarLyricsPublicationGate()

        assertTrue(gate.shouldPublish(key(mediaId = "1", primary = "First")))
        assertTrue(gate.shouldPublish(key(mediaId = "1", primary = "Second")))
        assertTrue(gate.shouldPublish(key(mediaId = "2", primary = "Second")))
        assertTrue(
            gate.shouldPublish(
                key(
                    mediaId = "2",
                    primary = "Second",
                    modes = CarArtworkMode.COVER
                )
            )
        )
    }

    @Test
    fun `reset permits republishing after an empty timeline`() {
        val gate = CarLyricsPublicationGate()
        val key = key(mediaId = "1", primary = "Current")

        assertTrue(gate.shouldPublish(key))
        gate.reset()

        assertTrue(gate.shouldPublish(key))
    }

    private fun key(
        mediaId: String,
        primary: String,
        modes: CarArtworkMode = CarArtworkMode.LYRICS
    ) = CarLyricsPublicationKey(
        mediaId = mediaId,
        primary = primary,
        secondary = "Next",
        modes = modes
    )
}
