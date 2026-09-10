package com.mardous.booming.playback.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarLyricsOverlayStateTest {
    private val state = CarLyricsOverlayState("loading card")

    @Test
    fun `transition keeps a card surface before the transition callbacks run`() {
        state.modes = CarArtworkMode.LYRICS
        state.publish("first", "first card")
        assertEquals("loading card", state.resolve("second").artwork)
    }

    @Test
    fun `late artwork cannot replace the current queue presentation`() {
        state.modes = CarArtworkMode.QUEUE
        state.publish("second", "second card")
        state.publish("first", "late first card")
        assertEquals("loading card", state.resolve("second").artwork)
    }

    @Test
    fun `first artwork render uses a placeholder without flashing the album`() {
        state.modes = CarArtworkMode.QUEUE
        state.publish("first", null)
        assertEquals("loading card", state.resolve("first").artwork)
    }

    @Test
    fun `switching between queue and lyrics discards the previous mode card`() {
        state.modes = CarArtworkMode.LYRICS
        state.publish("first", "lyrics card")
        state.modes = CarArtworkMode.QUEUE
        assertEquals("loading card", state.resolve("first").artwork)
        state.publish("first", "queue card")
        assertEquals("queue card", state.resolve("first").artwork)
    }

    @Test
    fun `disabling the mode restores the source artwork`() {
        state.modes = CarArtworkMode.QUEUE
        state.publish("first", "queue card")
        state.modes = CarArtworkMode.COVER
        assertNull(state.resolve("first").artwork)
        state.modes = CarArtworkMode.QUEUE
        assertEquals("loading card", state.resolve("first").artwork)
    }

    @Test
    fun `empty timeline has no overlay and clearing retains the selected surface`() {
        state.modes = CarArtworkMode.LYRICS
        state.publish("first", "first card")
        assertNull(state.resolve(null).artwork)
        state.clear()
        assertEquals("loading card", state.resolve("first").artwork)
    }
}
