package com.mardous.booming.playback.queue

internal class CarQueueSnapshots<T> {
    private data class Snapshot<T>(val revision: Long, val items: List<T>)
    private val snapshots = mutableMapOf<String, Snapshot<T>>()
    private var revision = 0L

    fun invalidate() { revision++ }

    fun remove(parentId: String) { snapshots.remove(parentId) }

    fun get(parentId: String, build: () -> List<T>): List<T> {
        val previous = snapshots[parentId]
        if (previous?.revision == revision) return previous.items
        val updated = build()
        val items = if (previous?.items == updated) previous.items else updated
        snapshots[parentId] = Snapshot(revision, items)
        return items
    }

    fun changed(parentId: String, build: () -> List<T>): List<T>? {
        val previous = snapshots[parentId]?.items
        val current = get(parentId, build)
        return current.takeUnless { it === previous }
    }
}
