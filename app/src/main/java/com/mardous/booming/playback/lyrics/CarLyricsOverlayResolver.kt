package com.mardous.booming.playback.lyrics

import androidx.media3.common.C
import androidx.media3.common.Player
import com.mardous.booming.core.model.lyrics.LyricsUiState
import com.mardous.booming.data.model.lyrics.SyncedLyrics
import kotlin.math.ceil

internal object CarLyricsOverlayResolver {

    data class Messages(
        val loading: String,
        val lyrics: String,
        val notSynchronized: String,
        val instrumental: String,
        val notFound: String,
        val error: String
    )

    data class Presentation(
        val primary: String,
        val secondary: String? = null,
        val nextUpdatePositionMs: Long? = null,
        val lineIndex: Int? = null,
        val lineStartPositionMs: Long? = null
    )

    fun resolve(
        state: LyricsUiState,
        currentMediaId: String,
        positionMs: Long,
        sourceTitle: String?,
        messages: Messages
    ): Presentation {
        val title = sanitizeLine(sourceTitle) ?: messages.lyrics
        if (state.mediaId != currentMediaId) return Presentation(title, messages.loading)
        return when (state) {
            is LyricsUiState.Loading -> Presentation(title, messages.loading)
            is LyricsUiState.NotFound -> Presentation(title, messages.notFound)
            is LyricsUiState.Error -> Presentation(title, messages.error)
            is LyricsUiState.Instrumental -> Presentation(title, messages.instrumental)
            is LyricsUiState.Plain -> Presentation(title, messages.notSynchronized)
            is LyricsUiState.Synced -> syncedPresentation(
                lyrics = state.syncedLyrics,
                positionMs = positionMs,
                sourceTitle = title,
                messages = messages
            )
        }
    }

    fun nextDelayMs(
        nextUpdatePositionMs: Long?,
        currentPositionMs: Long,
        isPlaying: Boolean,
        playbackState: Int,
        speed: Float
    ): Long? {
        if (nextUpdatePositionMs == null || !isPlaying || playbackState != Player.STATE_READY) {
            return null
        }
        val remainingMediaTimeMs = nextUpdatePositionMs - currentPositionMs
        if (remainingMediaTimeMs <= 0) return 0L
        return ceil(remainingMediaTimeMs / speed.coerceAtLeast(MIN_PLAYBACK_SPEED))
            .toLong()
            .coerceAtLeast(1L)
    }

    private fun syncedPresentation(
        lyrics: SyncedLyrics,
        positionMs: Long,
        sourceTitle: String,
        messages: Messages
    ): Presentation {
        val lines = lyrics.lines
        if (lines.isEmpty()) return Presentation(sourceTitle, messages.notFound)

        val lyricsPositionMs = positionMs + lyrics.offset
        val timelineIndex = findTimelineIndex(lines, lyricsPositionMs)
        if (timelineIndex == C.INDEX_UNSET) {
            return Presentation(
                primary = sourceTitle,
                secondary = lines.firstNotBlankContent(),
                nextUpdatePositionMs = (lines.first().start - lyrics.offset).coerceAtLeast(0L)
            )
        }

        val currentIndex = (timelineIndex downTo 0)
            .firstOrNull { sanitizeLine(lines[it].content.content) != null }
        val current = currentIndex
            ?.let { sanitizeLine(lines[it].content.content) }
            ?: messages.lyrics
        val next = ((timelineIndex + 1)..lines.lastIndex)
            .firstNotNullOfOrNull { sanitizeLine(lines[it].content.content) }
        return Presentation(
            primary = current,
            secondary = next,
            nextUpdatePositionMs = lines.getOrNull(timelineIndex + 1)?.start?.minus(lyrics.offset),
            lineIndex = currentIndex,
            lineStartPositionMs = currentIndex?.let { lines[it].start - lyrics.offset }
        )
    }

    private fun List<SyncedLyrics.Line>.firstNotBlankContent(): String? =
        firstNotNullOfOrNull { sanitizeLine(it.content.content) }

    private fun findTimelineIndex(lines: List<SyncedLyrics.Line>, positionMs: Long): Int {
        var low = 0
        var high = lines.lastIndex
        var result = C.INDEX_UNSET
        while (low <= high) {
            val middle = (low + high).ushr(1)
            if (lines[middle].start <= positionMs) {
                result = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return result
    }

    internal fun sanitizeLine(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val sanitized = buildString(value.length) {
            value.forEach { character ->
                when {
                    character.isWhitespace() -> append(' ')
                    !character.isISOControl() -> append(character)
                }
            }
        }.replace(HORIZONTAL_WHITESPACE, " ").trim()
        if (sanitized.isEmpty()) return null
        return sanitized
    }

    private val HORIZONTAL_WHITESPACE = Regex(" +")
    private const val MIN_PLAYBACK_SPEED = 0.01f
}
