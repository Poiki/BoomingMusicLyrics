package com.mardous.booming.data.mapper

import com.mardous.booming.data.model.Song
import com.mardous.booming.data.repository.RealSongRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongTextEncodingTest {
    @Test
    fun `old playlists history and play counts repair metadata while retaining file identity`() {
        val song = Song(
            id = 42, data = "/music/jab\u00C3\u00B3n.flac", title = "jab\u00C3\u00B3n",
            trackNumber = 1, year = 2026, size = 123, duration = 90_000,
            dateAdded = 1, rawDateModified = 2, albumId = 3, albumName = "Canci\u00C3\u00B3n",
            artistId = 4, artistName = "Jos\u00C3\u00A9 Mu\u00C3\u00B1oz",
            albumArtistName = "Jos\u00C3\u00A9", genreName = null
        )
        val restored = listOf(
            song.toSongEntity(5).toSong(),
            song.toHistoryEntity(6).toSong(),
            song.toPlayCount().toSong()
        )
        for (result in restored) {
            assertEquals("jabón", result.title)
            assertEquals("José Muñoz", result.artistName)
            assertEquals("José", result.albumArtistName)
            assertEquals("Canción", result.albumName)
            assertEquals(song.data, result.data)
            assertEquals(song.id, result.id)
            assertEquals(song.duration, result.duration)
        }
    }

    @Test
    fun `search parameters cover damaged metadata without bypassing library filters`() {
        val (selection, args) = RealSongRepository.generateSearchPattern("jabón")
        assertEquals(selection.count { it == '?' }, args.size)
        assertTrue(args.contains("%jabón%"))
        assertTrue(args.contains("%jab\u00C3\u00B3n%"))
        assertTrue(selection.startsWith("((") && selection.endsWith("))"))
        assertTrue(!selection.contains("jabón"))
    }

    @Test
    fun `album artist lookup includes the stored name with exact matching`() {
        val (selection, args) = RealSongRepository.generateSearchPattern(
            "José", "album_artist = ? COLLATE NOCASE", exact = true
        )
        assertEquals(selection.count { it == '?' }, args.size)
        assertTrue(args.contains("José"))
        assertTrue(args.contains("Jos\u00C3\u00A9"))
        assertTrue(args.none { it.contains('%') })
    }
}
