package com.mardous.booming.playback.lyrics

import android.content.SharedPreferences
import android.net.Uri
import com.mardous.booming.core.model.lyrics.LyricsUiState
import com.mardous.booming.core.model.queue.QueuePosition
import com.mardous.booming.data.model.QueueSong
import com.mardous.booming.data.model.Song
import com.mardous.booming.data.model.lyrics.LyricsActor
import com.mardous.booming.data.model.lyrics.LyricsFile
import com.mardous.booming.data.model.lyrics.LyricsSource
import com.mardous.booming.data.model.lyrics.RawLyrics
import com.mardous.booming.data.model.lyrics.SyncedLyrics
import com.mardous.booming.data.repository.LyricsRepository
import com.mardous.booming.playback.QueueStateHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentLyricsCoordinatorTest {

    @Test
    fun `indexed and external songs with the same numeric id keep distinct identities`() {
        val indexed = CurrentLyricsSongIdentity(id = 42, mediaId = "42")
        val external = CurrentLyricsSongIdentity(
            id = 42,
            mediaId = "/storage/emulated/0/Music/current song.flac"
        )

        assertFalse(indexed == external)
    }

    @Test
    fun `plain file is a fallback while later synchronized lyrics take priority`() = runTest {
        val repository = FakeLyricsRepository().apply {
            fileResult = { RawLyrics.File(lyricsFile, "plain file") }
            embeddedResult = { RawLyrics.Embedded("embedded synced") }
            parseResult = { _, raw ->
                if (raw.lyrics == "embedded synced") syncedLyrics("Embedded line") else null
            }
        }
        val fixture = createFixture(repository)

        fixture.select(song(1))
        advanceUntilIdle()

        val state = fixture.coordinator.lyricsUiState.value as LyricsUiState.Synced
        assertEquals("Embedded line", state.syncedLyrics.lines.single().content.content)
        assertEquals(1, repository.fileCalls[1])
        assertEquals(1, repository.embeddedCalls[1])
        assertEquals(null, repository.storedCalls[1])
    }

    @Test
    fun `instrumental not found and error remain distinct`() = runTest {
        val instrumentalRepository = FakeLyricsRepository().apply {
            storedResult = { RawLyrics.Stored(instrumental = true) }
        }
        val instrumental = createFixture(instrumentalRepository)
        instrumental.select(song(1))
        advanceUntilIdle()
        assertTrue(instrumental.coordinator.lyricsUiState.value is LyricsUiState.Instrumental)
        instrumental.cancelCollector()

        val missing = createFixture(FakeLyricsRepository())
        missing.select(song(2))
        advanceUntilIdle()
        assertTrue(missing.coordinator.lyricsUiState.value is LyricsUiState.NotFound)
        missing.cancelCollector()

        val failingRepository = FakeLyricsRepository().apply {
            fileResult = { throw IllegalStateException("disk failure") }
        }
        val failing = createFixture(failingRepository)
        failing.select(song(3))
        advanceUntilIdle()
        assertTrue(failing.coordinator.lyricsUiState.value is LyricsUiState.Error)
    }

    @Test
    fun `two subscribers share one request and negative results are cached`() = runTest {
        val repository = FakeLyricsRepository()
        val fixture = createFixture(repository, collectorCount = 2)
        val first = song(1)
        val second = song(2)

        fixture.select(first)
        advanceUntilIdle()
        fixture.select(second)
        advanceUntilIdle()
        fixture.select(first)
        advanceUntilIdle()

        assertEquals(1, repository.fileCalls[1])
        assertEquals(1, repository.embeddedCalls[1])
        assertEquals(1, repository.storedCalls[1])
        assertTrue(fixture.coordinator.lyricsUiState.value is LyricsUiState.NotFound)
    }

    @Test
    fun `errors are not cached`() = runTest {
        val repository = FakeLyricsRepository().apply {
            fileResult = { selected ->
                if (selected.id == 1L) throw IllegalStateException("read failure") else null
            }
        }
        val fixture = createFixture(repository)

        fixture.select(song(1))
        advanceUntilIdle()
        fixture.select(song(2))
        advanceUntilIdle()
        fixture.select(song(1))
        advanceUntilIdle()

        assertEquals(2, repository.fileCalls[1])
        assertTrue(fixture.coordinator.lyricsUiState.value is LyricsUiState.Error)
    }

    @Test
    fun `rapid A B C changes cancel obsolete work`() = runTest {
        val canceledIds = mutableSetOf<Long>()
        val repository = FakeLyricsRepository().apply {
            fileResult = { selected ->
                try {
                    delay(1_000)
                    RawLyrics.File(lyricsFile, "plain ${selected.id}")
                } finally {
                    if (!currentCoroutineContext().isActive) canceledIds += selected.id
                }
            }
        }
        val fixture = createFixture(repository)

        fixture.select(song(1))
        runCurrent()
        fixture.select(song(2))
        runCurrent()
        fixture.select(song(3))
        runCurrent()
        advanceTimeBy(1_000)
        advanceUntilIdle()

        val state = fixture.coordinator.lyricsUiState.value as LyricsUiState.Plain
        assertEquals(3, state.id)
        assertEquals("plain 3", state.lyrics)
        assertTrue(1L in canceledIds)
        assertTrue(2L in canceledIds)
        assertFalse(fixture.states.any { it.id in setOf(1L, 2L) && it !is LyricsUiState.Loading })
    }

    @Test
    fun `instrumental preference invalidates cached result`() = runTest {
        val preferences = TestPreferences().apply {
            putString(CurrentLyricsCoordinator.INSTRUMENTAL_TRACK_IDENTIFIERS, "Instrumental")
            putBoolean(CurrentLyricsCoordinator.MARK_INSTRUMENTAL_BY_TITLE, false)
        }
        val repository = FakeLyricsRepository()
        val fixture = createFixture(repository, preferences = preferences)

        fixture.select(song(1, title = "Instrumental version"))
        advanceUntilIdle()
        assertTrue(fixture.coordinator.lyricsUiState.value is LyricsUiState.NotFound)

        preferences.putBoolean(CurrentLyricsCoordinator.MARK_INSTRUMENTAL_BY_TITLE, true)
        advanceUntilIdle()

        assertTrue(fixture.coordinator.lyricsUiState.value is LyricsUiState.Instrumental)
        assertEquals(1, repository.invalidatedAllCount)
    }

    private fun TestScope.createFixture(
        repository: FakeLyricsRepository,
        preferences: TestPreferences = TestPreferences(),
        collectorCount: Int = 1
    ): Fixture {
        val queueStateHolder = QueueStateHolder()
        val coordinatorJob = SupervisorJob()
        val coordinatorDispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = CurrentLyricsCoordinator(
            preferences = preferences.instance,
            repository = repository,
            queueStateHolder = queueStateHolder,
            scope = CoroutineScope(coordinatorJob + coordinatorDispatcher),
            ioDispatcher = coordinatorDispatcher,
            errorLogger = { _, _ -> }
        )
        val states = mutableListOf<LyricsUiState>()
        val collectors = List(collectorCount) {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                coordinator.lyricsUiState.collect { states += it }
            }
        }
        runCurrent()
        val cancelActions = collectors.map { job -> { job.cancel() } } + { coordinatorJob.cancel() }
        return Fixture(queueStateHolder, coordinator, states, cancelActions)
    }

    private class Fixture(
        private val queueStateHolder: QueueStateHolder,
        val coordinator: CurrentLyricsCoordinator,
        val states: List<LyricsUiState>,
        private val cancelActions: List<() -> Unit>
    ) {
        fun select(vararg songs: Song, current: Int = 0) {
            val queue = songs.mapIndexed { index, value -> QueueSong(value.id to index, value) }
            queueStateHolder.submitQueue(queue, QueuePosition(current, IntArray(queue.size) { it }))
        }

        fun cancelCollector() {
            cancelActions.forEach { it() }
        }
    }

    private class FakeLyricsRepository : LyricsRepository {
        var fileResult: suspend (Song) -> RawLyrics.File? = { null }
        var embeddedResult: suspend (Song) -> RawLyrics.Embedded? = { null }
        var storedResult: suspend (Song) -> RawLyrics.Stored? = { null }
        var parseResult: suspend (Song, RawLyrics) -> SyncedLyrics? = { _, _ -> null }

        val fileCalls = mutableMapOf<Long, Int>()
        val embeddedCalls = mutableMapOf<Long, Int>()
        val storedCalls = mutableMapOf<Long, Int>()
        var invalidatedAllCount = 0

        override suspend fun parseRawLyrics(song: Song, rawLyrics: RawLyrics): SyncedLyrics? =
            parseResult(song, rawLyrics)

        override suspend fun fileLyrics(song: Song): RawLyrics.File? {
            fileCalls[song.id] = fileCalls.getOrDefault(song.id, 0) + 1
            return fileResult(song)
        }

        override suspend fun embeddedLyrics(song: Song): RawLyrics.Embedded? {
            embeddedCalls[song.id] = embeddedCalls.getOrDefault(song.id, 0) + 1
            return embeddedResult(song)
        }

        override suspend fun storedLyrics(song: Song, allowDownload: Boolean): RawLyrics.Stored? {
            storedCalls[song.id] = storedCalls.getOrDefault(song.id, 0) + 1
            return storedResult(song)
        }

        override suspend fun downloadLyrics(
            song: Song,
            searchTitle: String,
            searchArtist: String
        ): RawLyrics.Remote? = null

        override suspend fun saveLyrics(
            song: Song,
            originalLyricsBySource: Map<LyricsSource, RawLyrics?>,
            newContentBySource: Map<LyricsSource, String>
        ): Boolean? = null

        override suspend fun writableUris(song: Song): List<Uri> = emptyList()

        override suspend fun deleteAllLyrics() = Unit

        override fun invalidateCache(songId: Long?) {
            if (songId == null) invalidatedAllCount++
        }
    }

    private class TestPreferences {
        private val values = mutableMapOf<String, Any?>()
        private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

        val instance: SharedPreferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "getString" -> values[args!![0] as String] as? String ?: args[1]
                "getBoolean" -> values[args!![0] as String] as? Boolean ?: args[1]
                "registerOnSharedPreferenceChangeListener" -> {
                    listeners += args!![0] as SharedPreferences.OnSharedPreferenceChangeListener
                    null
                }
                "unregisterOnSharedPreferenceChangeListener" -> {
                    listeners -= args!![0] as SharedPreferences.OnSharedPreferenceChangeListener
                    null
                }
                "equals" -> proxy === args!![0]
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "TestPreferences"
                else -> throw UnsupportedOperationException(method.name)
            }
        } as SharedPreferences

        fun putString(key: String, value: String) {
            values[key] = value
            notifyChanged(key)
        }

        fun putBoolean(key: String, value: Boolean) {
            values[key] = value
            notifyChanged(key)
        }

        private fun notifyChanged(key: String) {
            listeners.toList().forEach { it.onSharedPreferenceChanged(instance, key) }
        }
    }

    private companion object {
        val lyricsFile = LyricsFile("lyrics.lrc", LyricsFile.Format.LRC)

        fun song(id: Long, title: String = "Song $id") = Song(
            id = id,
            data = "song-$id.mp3",
            title = title,
            trackNumber = 1,
            year = 2026,
            size = 1,
            duration = 10_000,
            dateAdded = 0,
            rawDateModified = 0,
            albumId = 1,
            albumName = "Album",
            artistId = 1,
            artistName = "Artist",
            albumArtistName = "Artist",
            genreName = "Genre"
        )

        fun syncedLyrics(text: String) = SyncedLyrics(
            lines = listOf(
                SyncedLyrics.Line(
                    start = 0,
                    end = 1_000,
                    content = SyncedLyrics.TextContent(text, null, null, emptyList()),
                    transliteration = null,
                    translation = null,
                    actor = LyricsActor.Male
                )
            )
        )
    }
}
