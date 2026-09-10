package com.mardous.booming.playback.queue

/** Visits only the visible portion of the queue, including the current item exactly once. */
internal fun carQueueWindow(
    currentIndex: Int,
    itemCount: Int,
    limit: Int,
    nextIndex: (Int) -> Int
): List<Int> {
    if (currentIndex !in 0 until itemCount || limit <= 0) return emptyList()
    val indices = ArrayList<Int>(minOf(itemCount, limit))
    var index = currentIndex
    while (index in 0 until itemCount && indices.size < limit && index !in indices) {
        indices.add(index)
        if (indices.size == limit) break
        index = nextIndex(index)
    }
    return indices
}
