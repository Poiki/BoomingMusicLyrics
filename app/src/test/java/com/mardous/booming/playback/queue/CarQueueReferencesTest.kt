package com.mardous.booming.playback.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarQueueReferencesTest {
    @Test
    fun `duplicate songs remain distinct queue occurrences`() {
        val references = CarQueueReferences<String>("session:")
        val first = references.register("song-1-occurrence-a", 0)
        val second = references.register("song-1-occurrence-b", 1)
        val timeline = listOf("song-1-occurrence-a", "song-1-occurrence-b")
        assertNotEquals(first, second)
        assertEquals(0, references.resolve(first, timeline.size, timeline::get))
        assertEquals(1, references.resolve(second, timeline.size, timeline::get))
    }

    @Test
    fun `unchanged queue selection reads one window even in a large queue`() {
        val references = CarQueueReferences<Int>("session:")
        val id = references.register(90000, 90000)
        var reads = 0
        assertEquals(90000, references.resolve(id, 100000) { reads++; it })
        assertEquals(1, reads)
    }

    @Test
    fun `moved items keep identity and deleted items cannot select their replacement`() {
        val references = CarQueueReferences<String>("session:")
        val id = references.register("chosen", 1)
        val moved = listOf("chosen", "replacement", "other")
        assertEquals(0, references.resolve(id, moved.size, moved::get))
        assertEquals(id, references.register("chosen", 0))
        val removed = listOf("replacement", "other")
        assertNull(references.resolve(id, removed.size, removed::get))
    }

    @Test
    fun `bounded references reject evicted and previous session ids`() {
        val references = CarQueueReferences<String>("session:", capacity = 2)
        val evicted = references.register("a", 0)
        val retained = references.register("b", 1)
        references.register("c", 2)
        assertNull(references.resolve(evicted, 3) { error("Evicted reference must not scan") })
        assertEquals(1, references.resolve(retained, 3) { listOf("a", "b", "c")[it] })
        assertNull(references.resolve("old-session:1", 3) { error("Unknown reference must not scan") })
    }
}
