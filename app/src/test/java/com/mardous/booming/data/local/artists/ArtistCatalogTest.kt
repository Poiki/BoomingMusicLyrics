package com.mardous.booming.data.local.artists

import com.mardous.booming.data.model.Album
import com.mardous.booming.data.model.Artist
import com.mardous.booming.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistCatalogTest {
    @Test
    fun `a collaboration appears under every participant without a combined artist`() {
        val catalog = ArtistCatalog(listOf(song(1, "SFDK"), song(2, "SFDK con Natos y Waor")))
        assertEquals(setOf("SFDK", "Natos y Waor"), catalog.entries.map { it.name }.toSet())
        assertEquals(listOf(1L, 2L), catalog.named("SFDK").songs.map { it.id })
        assertEquals(listOf(2L), catalog.named("Natos y Waor").songs.map { it.id })
    }

    @Test
    fun `supports explicit collaborator and multi-value tag separators`() {
        for (name in listOf(
            "SFDK feat. Natos y Waor", "SFDK ft. Natos y Waor", "SFDK featuring Natos y Waor",
            "SFDK (Feat. Natos y Waor)", "SFDK [ft. Natos y Waor]", "SFDK with Natos y Waor",
            "SFDK x Natos y Waor", "SFDK;Natos y Waor", "SFDK\u0000Natos y Waor", "SFDK\nNatos y Waor"
        )) {
            val catalog = ArtistCatalog(listOf(song(1, name)))
            assertEquals(name, setOf("SFDK", "Natos y Waor"), catalog.entries.map { it.name }.toSet())
        }
    }

    @Test
    fun `punctuation separates collaborations when a participant is known independently`() {
        for (separator in listOf(", ", " & ", " / ", "/", " + ")) {
            val catalog = ArtistCatalog(listOf(song(1, "SFDK"), song(2, "SFDK${separator}Natos y Waor")))
            assertEquals(separator, setOf("SFDK", "Natos y Waor"), catalog.entries.map { it.name }.toSet())
        }
    }

    @Test
    fun `preserves group names and ambiguous punctuation without independent evidence`() {
        val names = listOf("Natos y Waor", "AC/DC", "Earth, Wind & Fire", "Simon & Garfunkel", "Tyler, The Creator")
        val catalog = ArtistCatalog(names.mapIndexed { index, name -> song(index.toLong(), name) })
        assertEquals(names.toSet(), catalog.entries.map { it.name }.toSet())
    }

    @Test
    fun `deduplicates case spaces Unicode normalization and repeated credits`() {
        val catalog = ArtistCatalog(listOf(
            song(1, "SFDK"), song(2, " sfdk ; SFDK; SFDK "),
            song(3, "José Muñoz"), song(4, "Jose\u0301\u00A0 Muñoz"), song(5, "Jos\u00C3\u00A9 Mu\u00C3\u00B1oz")
        ))
        assertEquals(2, catalog.entries.size)
        assertEquals(2, catalog.named("SFDK").songs.size)
        assertEquals(3, catalog.named("José Muñoz").songs.size)
    }

    @Test
    fun `artist identity is stable across ordering unrelated additions and restarts`() {
        val tracks = listOf(song(1, "SFDK"), song(2, "SFDK con Natos y Waor"))
        val first = ArtistCatalog(tracks)
        val second = ArtistCatalog(tracks.reversed() + song(3, "Another artist"))
        for (name in listOf("SFDK", "Natos y Waor")) {
            assertEquals(first.named(name).id, second.named(name).id)
            assertTrue(first.named(name).id < -2)
        }
        assertNotEquals(first.named("SFDK").id, first.named("Natos y Waor").id)
        assertEquals(ArtistCredits.idForName("SFDK"), ArtistCredits.idForName(" sfdk "))
    }

    @Test
    fun `legacy source IDs open the primary artist with all collaborations`() {
        val catalog = ArtistCatalog(listOf(song(1, "SFDK"), song(2, "SFDK con Natos y Waor")))
        assertEquals(catalog.named("SFDK"), catalog.find(1))
        assertEquals(catalog.named("SFDK"), catalog.find(2))
        assertEquals(catalog.named("Natos y Waor"), catalog.find(catalog.named("Natos y Waor").id))
        assertEquals(null, catalog.find(999))
    }

    @Test
    fun `album members are attributed per song instead of inheriting the first track artist`() {
        val catalog = ArtistCatalog(listOf(song(1, "SFDK"), song(2, "Other artist"), song(3, "SFDK con Natos y Waor")))
        assertEquals(listOf(1L, 3L), catalog.named("SFDK").songs.map { it.id })
        assertEquals(listOf(2L), catalog.named("Other artist").songs.map { it.id })
        assertEquals(listOf(3L), catalog.named("Natos y Waor").songs.map { it.id })
        assertEquals(3, catalog.albumSongCounts[1])
    }

    @Test
    fun `repeated input rows do not duplicate artist songs or counts`() {
        val track = song(1, "SFDK;SFDK;Natos y Waor")
        val catalog = ArtistCatalog(listOf(track, track))
        assertTrue(catalog.entries.all { it.songs.size == 1 })
        assertEquals(1, catalog.albumSongCounts[1])
    }

    @Test
    fun `a guest appearance keeps its own name and does not turn an album into a single`() {
        val track = song(1, "SFDK con Natos y Waor")
        val album = Album(1, track.artistName, "SFDK", 2026, 0, listOf(track), totalSongCount = 12)
        val artist = Artist(ArtistCredits.idForName("Natos y Waor"), listOf(album), true, creditedName = "Natos y Waor")
        assertEquals("Natos y Waor", artist.name)
        assertFalse(album.isSingle)
        assertEquals(1, artist.albumCount)
        assertEquals(1, artist.songCount)
        assertEquals(1, artist.sortedAlbums.size)
        assertEquals("SFDK con Natos y Waor", artist.songs.single().artistName)
    }

    private fun ArtistCatalog.named(name: String) = entries.single { it.name == name }

    private fun song(id: Long, artist: String) = Song(
        id = id, data = "/music/$id.flac", title = "Track $id", trackNumber = id.toInt(), year = 2026,
        size = 100, duration = 90_000, dateAdded = 0, rawDateModified = 0,
        albumId = 1, albumName = "Shared album", artistId = id, artistName = artist,
        albumArtistName = null, genreName = null
    )
}
