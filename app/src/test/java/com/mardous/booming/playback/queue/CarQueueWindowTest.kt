package com.mardous.booming.playback.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarQueueWindowTest {
    @Test
    fun `current is first and large queues only visit visible successors`() {
        var visits = 0
        val result = carQueueWindow(40000, 100000, 5) { visits++; it + 1 }
        assertEquals(listOf(40000, 40001, 40002, 40003, 40004), result)
        assertEquals(4, visits)
    }

    @Test
    fun `shuffle follows the player order instead of incrementing indices`() {
        val order = mapOf(3 to 1, 1 to 4, 4 to 0, 0 to 2, 2 to -1)
        assertEquals(listOf(1, 4, 0, 2), carQueueWindow(1, 5, 5) { order.getValue(it) })
    }

    @Test
    fun `repeat all wraps without duplicating the current entry`() {
        assertEquals(listOf(2, 0, 1), carQueueWindow(2, 3, 5) { (it + 1) % 3 })
    }

    @Test
    fun `repeat one has no other upcoming songs`() {
        assertEquals(listOf(2), carQueueWindow(2, 10, 5) { it })
    }

    @Test
    fun `end and empty queues do not invent successors`() {
        assertEquals(listOf(2), carQueueWindow(2, 3, 5) { -1 })
        assertTrue(carQueueWindow(-1, 0, 5) { error("Unexpected traversal") }.isEmpty())
    }
}
