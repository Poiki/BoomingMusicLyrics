package com.mardous.booming.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.mardous.booming.data.local.EditTarget
import com.mardous.booming.data.local.MetadataReader
import com.mardous.booming.data.local.MetadataWriter
import com.mardous.booming.data.local.lyrics.LyricsFileDecoder
import com.mardous.booming.data.local.lyrics.lrc.LrcLyricsParser
import com.mardous.booming.data.local.lyrics.ttml.TtmlLyricsParser
import com.mardous.booming.data.local.room.LyricsDao
import com.mardous.booming.data.local.room.LyricsEntity
import com.mardous.booming.data.model.Song
import com.mardous.booming.data.model.UnindexedSong
import com.mardous.booming.data.model.lyrics.LyricsFile
import com.mardous.booming.data.model.lyrics.LyricsSource
import com.mardous.booming.data.model.lyrics.RawLyrics
import com.mardous.booming.data.model.lyrics.SyncedLyrics
import com.mardous.booming.data.remote.lyrics.LyricsDownloadService
import com.mardous.booming.data.text.repairMojibake
import com.mardous.booming.extensions.hasR
import com.mardous.booming.extensions.media.isArtistNameUnknown
import com.mardous.booming.util.Preferences.requireString
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.regex.Pattern

interface LyricsRepository {
    suspend fun parseRawLyrics(song: Song, rawLyrics: RawLyrics): SyncedLyrics?

    suspend fun fileLyrics(song: Song): RawLyrics.File?
    suspend fun embeddedLyrics(song: Song): RawLyrics.Embedded?
    suspend fun storedLyrics(song: Song, allowDownload: Boolean): RawLyrics.Stored?
    suspend fun downloadLyrics(song: Song, searchTitle: String, searchArtist: String): RawLyrics.Remote?

    suspend fun saveLyrics(
        song: Song,
        originalLyricsBySource: Map<LyricsSource, RawLyrics?>,
        newContentBySource: Map<LyricsSource, String>
    ): Boolean?

    suspend fun writableUris(song: Song): List<Uri>
    suspend fun deleteAllLyrics()
    fun invalidateCache(songId: Long? = null) = Unit
}

class RealLyricsRepository(
    private val context: Context,
    private val preferences: SharedPreferences,
    private val lyricsDownloadService: LyricsDownloadService,
    private val lyricsDao: LyricsDao
) : LyricsRepository {

    private val memoryCache = LruCache<LyricsCacheKey, Map<LyricsSource, RawLyrics>>(20)

    private val lrcLyricsParser = LrcLyricsParser()
    private val ttmlLyricsParser = TtmlLyricsParser()

    private val lyricsParsers = listOf(lrcLyricsParser, ttmlLyricsParser)

    override suspend fun parseRawLyrics(song: Song, rawLyrics: RawLyrics): SyncedLyrics? {
        val ignoreBlankLines = preferences.getBoolean(IGNORE_BLANK_LINES, false)
        try {
            return when (rawLyrics) {
                is RawLyrics.File -> {
                    if (rawLyrics.lyrics.isNotEmpty()) {
                        lyricsParsers.firstOrNull { it.handles(rawLyrics.file) }
                            ?.parse(rawLyrics.lyrics, song.duration, ignoreBlankLines)
                    } else null
                }

                is RawLyrics.Embedded -> {
                    if (!rawLyrics.lyrics.isNullOrEmpty()) {
                        lyricsParsers.firstOrNull { it.handles(rawLyrics.lyrics) }
                            ?.parse(rawLyrics.lyrics, song.duration, ignoreBlankLines)
                    } else null
                }

                is RawLyrics.Stored -> {
                    if (!rawLyrics.lyrics.isNullOrEmpty()) {
                        lyricsParsers.firstOrNull { it.handles(rawLyrics.lyrics) }
                            ?.parse(rawLyrics.lyrics, song.duration, ignoreBlankLines)
                            ?.copy(provider = rawLyrics.provider)
                    } else null
                }

                else -> null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't parse lyrics for song ${song.data}", e)
            throw e
        }
    }

    override suspend fun fileLyrics(song: Song): RawLyrics.File? {
        getCachedLyrics<RawLyrics.File>(LyricsSource.File, song)?.let { return it }
        try {
            val preferredFormatValue =
                preferences.requireString("preferred_lyrics_file_format", "ttml")
            val preferredFormat =
                LyricsFile.Format.entries.firstOrNull { it.value == preferredFormatValue }

            val rawLyricsList = mutableListOf<RawLyrics.File>()
            var firstReadFailure: Exception? = null
            for (file in findLyricsFiles(song)) {
                val actualFile = File(file.path)
                val lyrics = try {
                    LyricsFileDecoder.decode(
                        actualFile.readBytes(),
                        forceUtf8 = preferences.getBoolean(FORCE_UTF_8_ENCODING, true)
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (firstReadFailure == null) {
                        firstReadFailure = e
                    }
                    continue
                }

                if (lyrics.isNotEmpty()) {
                    val rawLyrics = RawLyrics.File(file, lyrics)
                    if (file.format == preferredFormat) {
                        return cacheLyrics(song, rawLyrics)
                    }
                    rawLyricsList.add(rawLyrics)
                }
            }

            return rawLyricsList.firstOrNull()?.let {
                cacheLyrics(song, it)
            } ?: firstReadFailure?.let { throw it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't find/read lyrics files for song ${song.data}", e)
            throw e
        }
        return null
    }

    override suspend fun embeddedLyrics(song: Song): RawLyrics.Embedded? {
        if (song.id != Song.emptySong.id) {
            getCachedLyrics<RawLyrics.Embedded>(LyricsSource.Embedded, song)?.let { return it }
            try {
                val metadataReader = MetadataReader(song.uri)
                var lyrics = metadataReader.value(MetadataReader.LYRICS)
                if (lyrics.isNullOrEmpty()) {
                    lyrics = metadataReader.value("UNSYNCEDLYRICS")
                }
                return cacheLyrics(song, RawLyrics.Embedded(lyrics?.repairMojibake()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't read embedded lyrics for song ${song.data}", e)
                throw e
            }
        }
        return null
    }

    override suspend fun storedLyrics(song: Song, allowDownload: Boolean): RawLyrics.Stored? {
        if (song.id != Song.emptySong.id) try {
            getCachedLyrics<RawLyrics.Stored>(LyricsSource.Downloaded, song)?.let { return it }
            if (song is UnindexedSong) {
                if (!allowDownload) return null
                return lyricsDownloadService.remoteLyrics(song)
                    .prepareToStore()
                    ?.let { cacheLyrics(song, it) }
            }
            val storedLyrics = lyricsDao.getLyrics(song.id)
            if (storedLyrics == null && allowDownload) {
                val storableLyrics = lyricsDownloadService.remoteLyrics(song)
                    .prepareToStore()
                if (storableLyrics != null) {
                    if (storableLyrics.instrumental) {
                        lyricsDao.insertLyrics(
                            LyricsEntity(song.id, instrumental = true)
                        )
                    } else {
                        lyricsDao.insertLyrics(
                            LyricsEntity(
                                id = song.id,
                                lyrics = storableLyrics.lyrics,
                                provider = storableLyrics.provider
                            )
                        )
                    }
                    return cacheLyrics(song, storableLyrics)
                }
            } else if (storedLyrics != null) {
                return cacheLyrics(song, RawLyrics.Stored(
                    lyrics = storedLyrics.lyrics?.repairMojibake(),
                    provider = storedLyrics.provider,
                    instrumental = storedLyrics.instrumental
                ))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't fetch/download lyrics for song ${song.data}", e)
            throw e
        }
        return null
    }

    override suspend fun downloadLyrics(
        song: Song,
        searchTitle: String,
        searchArtist: String
    ): RawLyrics.Remote? {
        if (song.id == Song.emptySong.id || searchArtist.isArtistNameUnknown()) {
            return null
        }
        return try {
            lyricsDownloadService.remoteLyrics(song, searchTitle, searchArtist, fromUser = true)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun saveLyrics(
        song: Song,
        originalLyricsBySource: Map<LyricsSource, RawLyrics?>,
        newContentBySource: Map<LyricsSource, String>
    ): Boolean? {
        try {
            val editedLyrics = newContentBySource.mapNotNull { (source, content) ->
                if (source == LyricsSource.File) return@mapNotNull null

                val originalLyrics = originalLyricsBySource[source] ?: when (source) {
                    LyricsSource.Embedded -> RawLyrics.Embedded(null)
                    LyricsSource.Downloaded -> RawLyrics.Stored()
                }
                if (originalLyrics.lyrics != content) {
                    RawLyrics.Edited(
                        originalLyrics = originalLyrics,
                        newContent = content
                    )
                } else null
            }

            if (editedLyrics.isEmpty()) {
                return null
            }

            return editedLyrics.all {
                when (it.originalLyrics) {
                    is RawLyrics.Embedded -> {
                        runCatching {
                            val metadataWriter = MetadataWriter()
                            metadataWriter.propertyMap(hashMapOf(MetadataReader.LYRICS to it.newContent))
                            metadataWriter.write(this.context, EditTarget.song(song)).isSuccess
                        }.getOrDefault(false).also { success ->
                            if (success) removeCachedLyrics(LyricsSource.Embedded, song)
                        }
                    }

                    is RawLyrics.Stored -> {
                        if (song is UnindexedSong) return@all false
                        runCatching {
                            lyricsDao.insertLyrics(
                                LyricsEntity(
                                    id = song.id,
                                    lyrics = it.newContent,
                                    provider = it.newContentProvider,
                                    instrumental = it.instrumental
                                )
                            )
                            true
                        }.getOrDefault(false).also { success ->
                            if (success) removeCachedLyrics(LyricsSource.Downloaded, song)
                        }
                    }

                    else -> false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while saving lyrics for song ${song.data}", e)
        }
        return false
    }

    override suspend fun writableUris(song: Song): List<Uri> {
        if (hasR()) {
            return listOf(song.uri).filterNot { it == Uri.EMPTY }
        }
        return emptyList()
    }

    override suspend fun deleteAllLyrics() {
        lyricsDao.removeLyrics()
        memoryCache.evictAll()
    }

    override fun invalidateCache(songId: Long?) {
        if (songId == null) {
            memoryCache.evictAll()
        } else {
            memoryCache.snapshot().keys
                .filter { it.songId == songId }
                .forEach(memoryCache::remove)
        }
    }

    private inline fun <reified T : RawLyrics> getCachedLyrics(
        source: LyricsSource,
        song: Song
    ): T? {
        val cachedLyrics = memoryCache[LyricsCacheKey(song.id, song.data)]
        if (cachedLyrics != null && cachedLyrics.containsKey(source)) {
            return cachedLyrics[source] as? T
        }
        return null
    }

    private fun <T : RawLyrics> cacheLyrics(song: Song, lyrics: T): T {
        val source = when (lyrics) {
            is RawLyrics.Embedded -> LyricsSource.Embedded
            is RawLyrics.Stored -> LyricsSource.Downloaded
            is RawLyrics.File -> LyricsSource.File
            else -> null
        }
        if (source != null) {
            val key = LyricsCacheKey(song.id, song.data)
            val cachedLyrics = memoryCache[key]?.toMutableMap() ?: mutableMapOf()
            cachedLyrics[source] = lyrics
            memoryCache.put(key, cachedLyrics)
        }
        return lyrics
    }

    private fun removeCachedLyrics(source: LyricsSource, song: Song) {
        val key = LyricsCacheKey(song.id, song.data)
        val cachedLyrics = memoryCache[key]?.toMutableMap()
        if (cachedLyrics != null) {
            cachedLyrics.remove(source)
            memoryCache.put(key, cachedLyrics)
        }
    }

    private fun findLyricsFiles(song: Song): List<LyricsFile> {
        val songFile = File(song.data)
        val parentDir = songFile.parentFile ?: return emptyList()

        val baseNames = listOf(
            songFile.nameWithoutExtension.repairMojibake(),
            "${song.artistName} - ${song.title}"
        ).filter { it.isNotBlank() }.map { Pattern.quote(it) }

        val patterns = baseNames.map { base ->
            Regex(".*$base.*\\.(lrc|ttml)", RegexOption.IGNORE_CASE)
        }

        return parentDir.listFiles()
            ?.filter { file ->
                file.isFile && patterns.any { it.matches(file.name.repairMojibake()) }
            }
            ?.mapNotNull { file ->
                val extension = file.extension.lowercase()
                LyricsFile.Format.entries.firstOrNull { it.value == extension }?.let { format ->
                    LyricsFile(file.absolutePath, format)
                }
            }
            .orEmpty()
    }

    companion object {
        private const val TAG = "LyricsRepository"

        private const val FORCE_UTF_8_ENCODING = "force_utf8_encoding_for_lyrics"
        private const val IGNORE_BLANK_LINES = "ignore_blank_lines_in_lyrics"
    }

    private data class LyricsCacheKey(
        val songId: Long,
        val data: String
    )
}
