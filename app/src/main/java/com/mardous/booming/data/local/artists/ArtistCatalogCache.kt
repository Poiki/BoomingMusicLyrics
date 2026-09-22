package com.mardous.booming.data.local.artists

import java.util.concurrent.atomic.AtomicLong

internal class ArtistCatalogCache<T : Any> {
    private data class Cached<T>(val revision: Long, val options: List<Any>, val value: T)

    private val revision = AtomicLong()
    @Volatile private var cached: Cached<T>? = null

    fun invalidate() {
        revision.incrementAndGet()
        cached = null
    }

    @Synchronized
    fun get(options: List<Any>, load: () -> T): T {
        while (true) {
            val currentRevision = revision.get()
            cached?.takeIf { it.revision == currentRevision && it.options == options }?.let { return it.value }
            val value = load()
            if (revision.get() == currentRevision) {
                cached = Cached(currentRevision, options, value)
                return value
            }
        }
    }
}
