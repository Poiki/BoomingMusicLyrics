package com.mardous.booming.data.local.artists

import com.mardous.booming.data.model.Song

internal class ArtistCatalog(songs: List<Song>) {
    data class Entry(val id: Long, val name: String, val songs: List<Song>)

    val entries: List<Entry>
    val albumSongCounts: Map<Long, Int>
    private val byId: Map<Long, Entry>
    private val sourceIds: Map<Long, Long>

    init {
        val uniqueSongs = songs.distinctBy { it.id to it.data }
        val credits = ArtistCredits(uniqueSongs.map { it.artistName }, uniqueSongs.mapNotNull { it.albumArtistName })
        val groups = linkedMapOf<String, MutableList<Song>>()
        val spellings = mutableMapOf<String, MutableMap<String, Int>>()
        val soloSpellings = mutableMapOf<String, MutableMap<String, Int>>()
        val aliases = mutableMapOf<Long, String>()
        for (song in uniqueSongs) {
            val participants = credits.participants(song.artistName)
            for (name in participants) {
                val key = name.artistKey()
                groups.getOrPut(key) { mutableListOf() }.add(song)
                spellings.getOrPut(key) { mutableMapOf() }.merge(name, 1, Int::plus)
                if (participants.size == 1) {
                    soloSpellings.getOrPut(key) { mutableMapOf() }.merge(name, 1, Int::plus)
                }
            }
            aliases.putIfAbsent(song.artistId, participants.first().artistKey())
        }
        entries = groups.map { (key, tracks) ->
            val names = soloSpellings[key] ?: spellings.getValue(key)
            val name = names.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .first().key
            Entry(ArtistCredits.idForName(key), name, tracks.toList())
        }
        byId = entries.associateBy { it.id }
        sourceIds = aliases.mapValues { ArtistCredits.idForName(it.value) }
        albumSongCounts = uniqueSongs.groupingBy { it.albumId }.eachCount()
    }

    fun find(id: Long): Entry? = byId[id] ?: sourceIds[id]?.let(byId::get)
}
