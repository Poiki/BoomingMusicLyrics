package com.mardous.booming.coil

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkMemoryCacheTest {
    @Test
    fun `eviction accounts for bytes and retains the recently used cover`() {
        val cache = ArtworkMemoryCache<String, String>(8, String::length)
        cache.put("current", "1234")
        cache.put("old", "5678")
        cache.get("current")
        cache.put("next", "abcd")

        assertEquals("1234", cache.get("current"))
        assertEquals("abcd", cache.get("next"))
        assertNull(cache.get("old"))
    }

    @Test
    fun `replacing an entry releases the old allocation`() {
        val cache = ArtworkMemoryCache<String, String>(8, String::length)
        cache.put("current", "12345678")
        cache.put("current", "12")
        cache.put("next", "345678")

        assertEquals("12", cache.get("current"))
        assertEquals("345678", cache.get("next"))
    }

    @Test
    fun `oversized artwork cannot evict every usable cover`() {
        val cache = ArtworkMemoryCache<String, String>(8, String::length)
        cache.put("current", "1234")
        cache.put("large", "123456789")

        assertEquals("1234", cache.get("current"))
        assertNull(cache.get("large"))
    }

    @Test
    fun `invalidating cached artwork releases its byte budget`() {
        val cache = ArtworkMemoryCache<String, String>(8, String::length)
        cache.put("old", "12345678")
        cache.clear()
        cache.put("new", "12345678")

        assertNull(cache.get("old"))
        assertEquals("12345678", cache.get("new"))
    }
}
