package com.mardous.booming.data.local.search

import com.mardous.booming.data.SearchFilter
import com.mardous.booming.data.model.search.SearchQuery.FilterMode
import com.mardous.booming.data.repository.Repository
import com.mardous.booming.data.text.repairMojibake
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

@Parcelize
class ArtistSearchFilter(
    private val label: String,
    private val artistId: Long,
    private val albumArtistName: String?
) : SearchFilter, KoinComponent {
    override fun getName(): CharSequence = label

    override fun getCompatibleModes(): List<FilterMode> = listOf(FilterMode.Songs, FilterMode.Albums)

    override suspend fun getResults(searchMode: FilterMode, query: String): List<Any> {
        val repository = get<Repository>()
        val artist = if (albumArtistName == null) repository.artistById(artistId)
            else repository.albumArtistByName(albumArtistName)
        val text = query.repairMojibake().trim()
        return when (searchMode) {
            FilterMode.Songs -> artist.sortedSongs.filter { it.title.contains(text, ignoreCase = true) }
            FilterMode.Albums -> artist.sortedAlbums.filter { it.name.contains(text, ignoreCase = true) }
            else -> emptyList()
        }
    }
}
