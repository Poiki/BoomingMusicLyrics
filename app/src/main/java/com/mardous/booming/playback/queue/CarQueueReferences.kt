package com.mardous.booming.playback.queue

/** Window identities distinguish duplicate songs and survive insertions and moves. */
internal class CarQueueReferences<Uid>(private val prefix: String, private val capacity: Int = 512) {
    private data class Entry<Uid>(val uid: Uid, var index: Int)

    private val entries = LinkedHashMap<String, Entry<Uid>>(16, 0.75f, true)
    private val ids = HashMap<Uid, String>()
    private var revision = 0L

    fun register(uid: Uid, index: Int): String {
        ids[uid]?.let { id ->
            entries.getValue(id).index = index
            return id
        }
        val id = prefix + (++revision).toString(36)
        entries[id] = Entry(uid, index)
        ids[uid] = id
        if (entries.size > capacity) {
            val iterator = entries.entries.iterator()
            ids.remove(iterator.next().value.uid)
            iterator.remove()
        }
        return id
    }

    fun resolve(id: String, itemCount: Int, uidAt: (Int) -> Uid): Int? {
        val entry = entries[id] ?: return null
        if (entry.index in 0 until itemCount && uidAt(entry.index) == entry.uid) return entry.index
        // Only a selection after a structural edit needs to search the timeline.
        val index = (0 until itemCount).firstOrNull { uidAt(it) == entry.uid } ?: return null
        entry.index = index
        return index
    }
}
