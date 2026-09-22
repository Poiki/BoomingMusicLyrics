package com.mardous.booming.data.local.artists

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ArtistCatalogCacheTest {
    @Test
    fun `browsing details reuses one library scan`() {
        val cache = ArtistCatalogCache<Any>()
        var reads = 0
        val load = { reads++; Any() }
        val first = cache.get(listOf(false), load)
        repeat(100) { assertSame(first, cache.get(listOf(false), load)) }
        assertEquals(1, reads)
    }

    @Test
    fun `metadata and filter changes rebuild the index`() {
        val cache = ArtistCatalogCache<Int>()
        var reads = 0
        val load = { ++reads }
        assertEquals(1, cache.get(listOf(false), load))
        cache.invalidate()
        assertEquals(2, cache.get(listOf(false), load))
        assertEquals(3, cache.get(listOf(true), load))
        assertEquals(3, cache.get(listOf(true), load))
    }

    @Test
    fun `a media change during a scan cannot publish the stale index`() {
        val cache = ArtistCatalogCache<Int>()
        var reads = 0
        val result = cache.get(emptyList()) {
            reads++
            if (reads == 1) cache.invalidate()
            reads
        }
        assertEquals(2, result)
        assertEquals(2, cache.get(emptyList()) { 99 })
    }
}
