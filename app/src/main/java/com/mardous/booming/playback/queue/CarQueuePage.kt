package com.mardous.booming.playback.queue

internal data class CarQueuePage(val indices: List<Int>, val nextIndex: Int?)

internal fun carQueuePage(
    startIndex: Int,
    stopIndex: Int,
    itemCount: Int,
    limit: Int,
    nextIndex: (Int) -> Int
): CarQueuePage {
    if (startIndex !in 0 until itemCount || limit <= 0) return CarQueuePage(emptyList(), null)
    val indices = LinkedHashSet<Int>(minOf(itemCount, limit))
    var index = startIndex
    while (indices.size < limit) {
        indices.add(index)
        val next = nextIndex(index)
        if (next !in 0 until itemCount || next == stopIndex || next in indices) {
            return CarQueuePage(indices.toList(), null)
        }
        index = next
    }
    return CarQueuePage(indices.toList(), index)
}

internal fun <T> carRootChildren(categories: List<T>, queue: T, library: T, limit: Int): List<T> {
    val capacity = limit.coerceAtLeast(1)
    return when {
        capacity == 1 -> listOf(library)
        categories.size < capacity -> listOf(queue) + categories
        else -> listOf(queue) + categories.take(capacity - 2) + library
    }
}
