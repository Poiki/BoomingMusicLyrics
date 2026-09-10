package com.mardous.booming.coil

internal class ArtworkMemoryCache<Key, Value>(
    private val maxBytes: Int,
    private val sizeOf: (Value) -> Int
) {
    private val entries = LinkedHashMap<Key, Value>(4, 0.75f, true)
    private var byteCount = 0

    @Synchronized
    fun get(key: Key): Value? = entries[key]

    @Synchronized
    fun put(key: Key, value: Value) {
        val size = sizeOf(value)
        entries.remove(key)?.let { byteCount -= sizeOf(it) }
        if (size > maxBytes) return
        entries[key] = value
        byteCount += size
        while (byteCount > maxBytes) {
            val oldest = entries.entries.first()
            byteCount -= sizeOf(oldest.value)
            entries.remove(oldest.key)
        }
    }

    @Synchronized
    fun clear() {
        entries.clear()
        byteCount = 0
    }
}
