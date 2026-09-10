package com.mardous.booming.data.model.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RawLyricsTest {

    @Test
    fun `instrumental remote result survives provider merge and storage`() {
        val instrumental = RawLyrics.Remote(instrumental = true)
        val combined = RawLyrics.Remote().accept(instrumental)
        val stored = combined.prepareToStore()

        assertNotNull(stored)
        assertTrue(stored!!.instrumental)
    }

    @Test
    fun `real lyrics replace an earlier instrumental marker`() {
        val combined = RawLyrics.Remote(instrumental = true).accept(
            RawLyrics.Remote(
                synced = RawLyrics.Remote.Content("provider", "[00:01.00]Line")
            )
        )
        val stored = combined.prepareToStore()

        assertNotNull(stored)
        assertEquals("[00:01.00]Line", stored!!.lyrics)
        assertEquals(false, stored.instrumental)
    }
}
