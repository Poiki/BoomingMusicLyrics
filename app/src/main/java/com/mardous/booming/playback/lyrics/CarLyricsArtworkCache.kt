package com.mardous.booming.playback.lyrics

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shares a pending pre-render with publication and bounds decoded artwork retained in memory. */
internal class CarLyricsArtworkCache<Key, Artwork>(private val maxEntries: Int = 3) {
    private val mutex = Mutex()
    private val entries = LinkedHashMap<Key, Artwork>(maxEntries, 0.75f, true)

    fun get(key: Key): Artwork? {
        if (!mutex.tryLock()) return null
        return try {
            entries[key]
        } finally {
            mutex.unlock()
        }
    }

    suspend fun getOrRender(key: Key, render: suspend () -> Artwork): Artwork = mutex.withLock {
        entries[key]?.let { return@withLock it }
        val artwork = render()
        entries[key] = artwork
        while (entries.size > maxEntries) {
            entries.remove(entries.entries.first().key)
        }
        artwork
    }
}
