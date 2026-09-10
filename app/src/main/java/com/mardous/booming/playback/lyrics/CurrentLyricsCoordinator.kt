package com.mardous.booming.playback.lyrics

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.util.Log
import com.mardous.booming.core.model.lyrics.LyricsUiState
import com.mardous.booming.data.local.lyrics.InstrumentalDetector
import com.mardous.booming.data.model.Song
import com.mardous.booming.data.model.UnindexedSong
import com.mardous.booming.data.model.lyrics.LyricsSource
import com.mardous.booming.data.model.network.NetworkFeature
import com.mardous.booming.data.repository.LyricsRepository
import com.mardous.booming.playback.QueueStateHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentLyricsCoordinator(
    private val preferences: SharedPreferences,
    private val repository: LyricsRepository,
    queueStateHolder: QueueStateHolder,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val errorLogger: (String, Throwable) -> Unit = { message, error ->
        Log.e(TAG, message, error)
    }
) : OnSharedPreferenceChangeListener {

    private val refreshCounter = MutableStateFlow(0L)
    private val terminalCache = TerminalLyricsCache(CACHE_SIZE)

    @Volatile
    private var currentSongIdentity = CurrentLyricsSongIdentity(
        id = Song.emptySong.id,
        mediaId = Song.emptySong.id.toString()
    )

    val lyricsUiState: StateFlow<LyricsUiState> = combine(
        queueStateHolder.currentSong.distinctUntilChangedBy(Song::currentLyricsIdentity),
        refreshCounter
    ) { song, _ -> song }
        .transformLatest { song ->
            val identity = song.currentLyricsIdentity()
            currentSongIdentity = identity

            if (song == Song.emptySong) {
                emit(LyricsUiState.NotFound(song.id, identity.mediaId))
                return@transformLatest
            }

            terminalCache.get(identity)?.let { cached ->
                emit(cached)
                return@transformLatest
            }

            emit(LyricsUiState.Loading(song.id, identity.mediaId))
            val terminalState = try {
                resolveLyrics(song, identity.mediaId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorLogger("Couldn't resolve lyrics for song ${song.data}", e)
                LyricsUiState.Error(song.id, e.message, identity.mediaId)
            }
            if (terminalState !is LyricsUiState.Error) {
                terminalCache.put(identity, terminalState)
            }
            emit(terminalState)
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = LyricsUiState.NotFound(Song.emptySong.id)
        )

    init {
        preferences.registerOnSharedPreferenceChangeListener(this)
    }

    fun invalidate(songId: Long? = null) {
        repository.invalidateCache(songId)
        if (songId == null) {
            terminalCache.clear()
        } else {
            terminalCache.remove(songId)
        }

        if (songId == null || songId == currentSongIdentity.id) {
            refreshCounter.update { it + 1 }
        }
    }

    internal fun songIdForMediaId(mediaId: String): Long? =
        currentSongIdentity.id.takeIf { currentSongIdentity.mediaId == mediaId }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key in INVALIDATING_PREFERENCE_KEYS) {
            invalidate()
        }
    }

    private suspend fun resolveLyrics(
        song: Song,
        mediaId: String
    ): LyricsUiState = withContext(ioDispatcher) {
        val instrumentalDetector = createInstrumentalDetector()
        var plainLyrics: String? = null
        var firstFailure: Exception? = null

        suspend fun <T> attempt(source: LyricsSource, operation: suspend () -> T): T? {
            return try {
                operation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (firstFailure == null) {
                    firstFailure = e
                }
                errorLogger("Couldn't resolve $source lyrics for song ${song.data}", e)
                null
            }
        }

        if (instrumentalDetector.byTitle(song.title)) {
            return@withContext LyricsUiState.Instrumental(song.id, mediaId)
        }

        for (source in SOURCE_PRIORITY) {
            when (source) {
                LyricsSource.File -> {
                    val fileLyrics = attempt(source) { repository.fileLyrics(song) } ?: continue
                    if (instrumentalDetector.byLyrics(fileLyrics.lyrics)) {
                        return@withContext LyricsUiState.Instrumental(song.id, mediaId)
                    }

                    val lyrics = attempt(source) { repository.parseRawLyrics(song, fileLyrics) }
                    if (lyrics?.hasContent == true) {
                        return@withContext LyricsUiState.Synced(song.id, lyrics, mediaId)
                    }
                    if (plainLyrics.isNullOrEmpty()) {
                        plainLyrics = fileLyrics.lyrics.takeIf { it.isNotBlank() }
                    }
                }

                LyricsSource.Embedded -> {
                    val embeddedLyrics = attempt(source) { repository.embeddedLyrics(song) }
                        ?: continue
                    if (instrumentalDetector.byLyrics(embeddedLyrics.lyrics)) {
                        return@withContext LyricsUiState.Instrumental(song.id, mediaId)
                    }

                    val lyrics = attempt(source) { repository.parseRawLyrics(song, embeddedLyrics) }
                    if (lyrics?.hasContent == true) {
                        return@withContext LyricsUiState.Synced(song.id, lyrics, mediaId)
                    }
                    if (plainLyrics.isNullOrEmpty()) {
                        plainLyrics = embeddedLyrics.lyrics
                    }
                }

                LyricsSource.Downloaded -> {
                    val downloadedLyrics = attempt(source) {
                        repository.storedLyrics(song, allowDownload = true)
                    } ?: continue
                    if (downloadedLyrics.instrumental) {
                        return@withContext LyricsUiState.Instrumental(song.id, mediaId)
                    }

                    val lyrics = attempt(source) { repository.parseRawLyrics(song, downloadedLyrics) }
                    if (lyrics?.hasContent == true) {
                        return@withContext LyricsUiState.Synced(song.id, lyrics, mediaId)
                    }
                    if (plainLyrics.isNullOrEmpty()) {
                        plainLyrics = downloadedLyrics.lyrics
                    }
                }
            }
        }

        plainLyrics
            ?.takeIf { it.isNotBlank() }
            ?.let { LyricsUiState.Plain(song.id, it, mediaId) }
            ?: firstFailure?.let { LyricsUiState.Error(song.id, it.message, mediaId) }
            ?: LyricsUiState.NotFound(song.id, mediaId)
    }

    private fun createInstrumentalDetector() = InstrumentalDetector(
        identifiers = preferences.getString(INSTRUMENTAL_TRACK_IDENTIFIERS, null)
            ?.split(",")
            .orEmpty()
            .toSet(),
        markByTitle = preferences.getBoolean(MARK_INSTRUMENTAL_BY_TITLE, false),
        maxLength = INSTRUMENTAL_IDENTIFIER_MAX_LENGTH
    )

    companion object {
        const val INSTRUMENTAL_TRACK_IDENTIFIERS = "instrumental_track_identifiers"
        const val MARK_INSTRUMENTAL_BY_TITLE = "mark_instrumental_tracks_by_title"

        private const val TAG = "CurrentLyricsCoordinator"
        private const val CACHE_SIZE = 20
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val INSTRUMENTAL_IDENTIFIER_MAX_LENGTH = 50

        private val INVALIDATING_PREFERENCE_KEYS = setOf(
            INSTRUMENTAL_TRACK_IDENTIFIERS,
            MARK_INSTRUMENTAL_BY_TITLE,
            PREFERRED_LYRICS_FILE_FORMAT,
            IGNORE_BLANK_LINES,
            FORCE_UTF_8_ENCODING,
            NetworkFeature.NETWORK_FEATURES_KEY,
            NetworkFeature.BETTERLYRICS_ENABLED_KEY,
            NetworkFeature.LYRICALLY_ENABLED_KEY,
            NetworkFeature.LRCLIB_ENABLED_KEY
        )

        private val SOURCE_PRIORITY = listOf(
            LyricsSource.File,
            LyricsSource.Embedded,
            LyricsSource.Downloaded
        )

        private const val PREFERRED_LYRICS_FILE_FORMAT = "preferred_lyrics_file_format"
        private const val IGNORE_BLANK_LINES = "ignore_blank_lines_in_lyrics"
        private const val FORCE_UTF_8_ENCODING = "force_utf8_encoding_for_lyrics"
    }
}

internal data class CurrentLyricsSongIdentity(
    val id: Long,
    val mediaId: String
)

internal fun Song.currentLyricsIdentity() = CurrentLyricsSongIdentity(
    id = id,
    mediaId = if (this is UnindexedSong) data else id.toString()
)

private class TerminalLyricsCache(private val maxSize: Int) {
    private val values = LinkedHashMap<CurrentLyricsSongIdentity, LyricsUiState>(
        maxSize,
        0.75f,
        true
    )

    @Synchronized
    fun get(identity: CurrentLyricsSongIdentity): LyricsUiState? = values[identity]

    @Synchronized
    fun put(identity: CurrentLyricsSongIdentity, state: LyricsUiState) {
        values[identity] = state
        while (values.size > maxSize) {
            val eldestKey = values.entries.firstOrNull()?.key ?: break
            values.remove(eldestKey)
        }
    }

    @Synchronized
    fun remove(songId: Long) {
        values.keys.removeAll { it.id == songId }
    }

    @Synchronized
    fun clear() {
        values.clear()
    }
}
