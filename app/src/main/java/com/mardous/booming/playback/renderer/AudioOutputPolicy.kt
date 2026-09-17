package com.mardous.booming.playback.renderer

internal fun preferHighPrecisionAudio(explicitPreference: Boolean?, processingEnabled: Boolean): Boolean =
    explicitPreference ?: !processingEnabled

internal fun needsOffloadSpeedSupport(speed: Float, pitch: Float): Boolean = speed != 1f || pitch != 1f
