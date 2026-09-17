package com.mardous.booming.playback.renderer

import org.junit.Assert.*
import org.junit.Test

class AudioOutputPolicyTest {
    @Test fun `neutral playback defaults to high precision`() {
        assertTrue(preferHighPrecisionAudio(null, false))
    }
    @Test fun `existing sound processing is preserved by default`() {
        assertFalse(preferHighPrecisionAudio(null, true))
    }
    @Test fun `explicit user choices override automatic defaults`() {
        assertFalse(preferHighPrecisionAudio(false, false))
        assertTrue(preferHighPrecisionAudio(true, true))
    }
    @Test fun `normal playback does not unnecessarily exclude hardware offload`() {
        assertFalse(needsOffloadSpeedSupport(1f, 1f))
        assertTrue(needsOffloadSpeedSupport(1.2f, 1f))
        assertTrue(needsOffloadSpeedSupport(1f, 0.9f))
    }
}
