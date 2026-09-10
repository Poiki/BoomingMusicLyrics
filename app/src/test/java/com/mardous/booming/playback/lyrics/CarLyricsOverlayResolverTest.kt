package com.mardous.booming.playback.lyrics

import androidx.media3.common.Player
import com.mardous.booming.core.model.lyrics.LyricsUiState
import com.mardous.booming.data.model.lyrics.SyncedLyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CarLyricsOverlayResolverTest {

    @Test
    fun `synced lyrics apply offset and change only at timestamp boundaries`() {
        val state = syncedState(offset = 500, line(1_000, "First"), line(2_000, "Second"))

        val before = resolve(state, positionMs = 499)
        val first = resolve(state, positionMs = 500)
        val sameFirst = resolve(state, positionMs = 1_499)
        val second = resolve(state, positionMs = 1_500)

        assertEquals("Song", before.primary)
        assertEquals("First", before.secondary)
        assertEquals(500L, before.nextUpdatePositionMs)
        assertEquals("First", first.primary)
        assertEquals("Second", first.secondary)
        assertEquals(first, sameFirst)
        assertEquals("Second", second.primary)
        assertNull(second.secondary)
        assertNull(second.nextUpdatePositionMs)
    }

    @Test
    fun `empty timestamp lines keep previous text and select next non-empty line`() {
        val state = syncedState(
            offset = 0,
            line(0, "First"),
            line(1_000, "  \n\t "),
            line(2_000, "Third")
        )

        val presentation = resolve(state, positionMs = 1_000)

        assertEquals("First", presentation.primary)
        assertEquals("Third", presentation.secondary)
        assertEquals(2_000L, presentation.nextUpdatePositionMs)
    }

    @Test
    fun `non-synced states preserve title and expose only approved status`() {
        assertEquals("Loading", resolve(LyricsUiState.Loading(1)).secondary)
        assertEquals("Not synchronized", resolve(LyricsUiState.Plain(1, "Secret lyrics")).secondary)
        assertEquals("Instrumental", resolve(LyricsUiState.Instrumental(1)).secondary)
        assertEquals("Not found", resolve(LyricsUiState.NotFound(1)).secondary)
        assertEquals("Error", resolve(LyricsUiState.Error(1, "Sensitive error")).secondary)
        assertEquals("Song", resolve(LyricsUiState.Error(1, "Sensitive error")).primary)
    }

    @Test
    fun `stale lyrics from a previous song are never exposed`() {
        val state = syncedState(offset = 0, line(0, "Previous song secret"))

        val result = CarLyricsOverlayResolver.resolve(
            state = state,
            currentMediaId = "2",
            positionMs = 500,
            sourceTitle = "Current song",
            messages = messages
        )

        assertEquals("Current song", result.primary)
        assertEquals("Loading", result.secondary)
        assertNull(result.lineIndex)
        assertNull(result.lineStartPositionMs)
        assertNull(result.nextUpdatePositionMs)
    }

    @Test
    fun `same numeric song id cannot expose indexed lyrics on an external file`() {
        val indexedState = LyricsUiState.Synced(
            id = 1,
            syncedLyrics = SyncedLyrics(listOf(line(0, "Indexed song secret")), 0),
            mediaId = "1"
        )

        val result = CarLyricsOverlayResolver.resolve(
            state = indexedState,
            currentMediaId = "/storage/emulated/0/Music/external.flac",
            positionMs = 500,
            sourceTitle = "External file",
            messages = messages
        )

        assertEquals("External file", result.primary)
        assertEquals("Loading", result.secondary)
    }

    @Test
    fun `sanitization preserves unicode rtl and long content while flattening whitespace`() {
        val unicode = "  مرحبا\n🌍\tשלום  "
        val sanitized = CarLyricsOverlayResolver.sanitizeLine(unicode)
        val long = CarLyricsOverlayResolver.sanitizeLine("😀".repeat(501))

        assertEquals("مرحبا 🌍 שלום", sanitized)
        assertEquals(501, long!!.codePointCount(0, long.length))
        assertFalse(long.last().isHighSurrogate())
    }

    @Test
    fun `delay follows speed and is canceled while paused or not ready`() {
        assertEquals(1_000L, delay(next = 3_000, current = 1_000, speed = 2f))
        assertEquals(4_000L, delay(next = 3_000, current = 1_000, speed = 0.5f))
        assertEquals(0L, delay(next = 1_000, current = 1_500))
        assertNull(delay(next = 3_000, current = 1_000, isPlaying = false))
        assertNull(delay(next = 3_000, current = 1_000, playbackState = Player.STATE_BUFFERING))
    }

    private fun resolve(state: LyricsUiState, positionMs: Long = 0) =
        CarLyricsOverlayResolver.resolve(
            state = state,
            currentMediaId = "1",
            positionMs = positionMs,
            sourceTitle = "Song",
            messages = messages
        )

    private fun delay(
        next: Long,
        current: Long,
        isPlaying: Boolean = true,
        playbackState: Int = Player.STATE_READY,
        speed: Float = 1f
    ) = CarLyricsOverlayResolver.nextDelayMs(
        nextUpdatePositionMs = next,
        currentPositionMs = current,
        isPlaying = isPlaying,
        playbackState = playbackState,
        speed = speed
    )

    private fun syncedState(offset: Long, vararg lines: SyncedLyrics.Line) =
        LyricsUiState.Synced(1, SyncedLyrics(lines.toList(), offset))

    private fun line(start: Long, text: String) = SyncedLyrics.Line(
        start = start,
        end = start + 1_000,
        content = SyncedLyrics.TextContent(text, null, null, emptyList()),
        transliteration = null,
        translation = null,
        actor = null
    )

    private companion object {
        val messages = CarLyricsOverlayResolver.Messages(
            loading = "Loading",
            lyrics = "Lyrics",
            notSynchronized = "Not synchronized",
            instrumental = "Instrumental",
            notFound = "Not found",
            error = "Error"
        )
    }
}
