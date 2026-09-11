package com.mardous.booming.playback.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarQueuePageTest {
    @Test
    fun `large queues traverse only one page`() {
        var visits = 0
        val page = carQueuePage(40000, 40000, 100000, 40) { visits++; it + 1 }
        assertEquals((40000..40039).toList(), page.indices)
        assertEquals(40040, page.nextIndex)
        assertEquals(40, visits)
    }

    @Test
    fun `shuffle pages wrap once and never duplicate the root anchor`() {
        val order = mapOf(3 to 1, 1 to 4, 4 to 0, 0 to 2, 2 to 3)
        val first = carQueuePage(3, 3, 5, 2) { order.getValue(it) }
        val second = carQueuePage(first.nextIndex!!, 3, 5, 2) { order.getValue(it) }
        val third = carQueuePage(second.nextIndex!!, 3, 5, 2) { order.getValue(it) }
        assertEquals(listOf(3, 1, 4, 0, 2), first.indices + second.indices + third.indices)
        assertNull(third.nextIndex)
    }

    @Test
    fun `empty end repeat one and exact page boundaries have no more row`() {
        assertTrue(carQueuePage(-1, -1, 0, 40) { error("Empty traversal") }.indices.isEmpty())
        assertEquals(CarQueuePage(listOf(2), null), carQueuePage(2, 2, 5, 40) { it })
        assertEquals(CarQueuePage(listOf(3, 4), null), carQueuePage(3, 3, 5, 2) {
            if (it == 4) -1 else it + 1
        })
    }

    @Test
    fun `root reserves a library folder when car tabs cannot fit all categories`() {
        val categories = listOf("Songs", "Albums", "Artists", "Playlists", "Genres")
        assertEquals(listOf("Up Next", "Songs", "Albums", "Library"),
            carRootChildren(categories, "Up Next", "Library", 4))
        assertEquals(listOf("Up Next", "Library"),
            carRootChildren(categories, "Up Next", "Library", 2))
        assertEquals(listOf("Library"), carRootChildren(categories, "Up Next", "Library", 1))
        assertEquals(listOf("Up Next", "Songs", "Albums"),
            carRootChildren(categories.take(2), "Up Next", "Library", 4))
    }
}
