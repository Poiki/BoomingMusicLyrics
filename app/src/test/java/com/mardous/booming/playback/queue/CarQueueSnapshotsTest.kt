package com.mardous.booming.playback.queue

import org.junit.Assert.*
import org.junit.Test

class CarQueueSnapshotsTest {
    @Test
    fun `preparation and duplicate events do not republish identical rows`() {
        val cache = CarQueueSnapshots<String>()
        val original = cache.get("root") { listOf("current", "next") }
        repeat(10) { cache.invalidate() }
        var builds = 0
        assertNull(cache.changed("root") { builds++; listOf("current", "next") })
        assertSame(original, cache.get("root") { error("Already refreshed") })
        assertEquals(1, builds)
    }

    @Test
    fun `a track change publishes once and children reuse the prepared result`() {
        val cache = CarQueueSnapshots<String>()
        cache.get("root") { listOf("a", "b", "c") }
        cache.invalidate()
        val changed = cache.changed("root") { listOf("b", "c") }
        assertEquals(listOf("b", "c"), changed)
        assertSame(changed, cache.get("root") { error("Must reuse snapshot") })
        assertNull(cache.changed("root") { error("No new invalidation") })
    }

    @Test
    fun `empty queues notify and unused pages are not rebuilt`() {
        val cache = CarQueueSnapshots<String>()
        cache.get("root") { listOf("song") }
        cache.get("more") { listOf("later") }
        cache.invalidate()
        assertEquals(emptyList<String>(), cache.changed("root") { emptyList() })
        cache.remove("more")
        assertEquals(listOf("new"), cache.get("more") { listOf("new") })
    }
}
