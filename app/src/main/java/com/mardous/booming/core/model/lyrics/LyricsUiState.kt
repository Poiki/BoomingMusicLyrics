package com.mardous.booming.core.model.lyrics

import com.mardous.booming.data.model.lyrics.SyncedLyrics

sealed class LyricsUiState(
    open val id: Long,
    open val mediaId: String
) {
    data class Loading(
        override val id: Long,
        override val mediaId: String = id.toString()
    ) : LyricsUiState(id, mediaId)

    data class NotFound(
        override val id: Long,
        override val mediaId: String = id.toString()
    ) : LyricsUiState(id, mediaId)

    data class Error(
        override val id: Long,
        val message: String? = null,
        override val mediaId: String = id.toString()
    ) : LyricsUiState(id, mediaId)

    data class Instrumental(
        override val id: Long,
        override val mediaId: String = id.toString()
    ) : LyricsUiState(id, mediaId)

    data class Plain(
        override val id: Long,
        val lyrics: String,
        override val mediaId: String = id.toString()
    ) : LyricsUiState(id, mediaId)

    data class Synced(
        override val id: Long,
        val syncedLyrics: SyncedLyrics,
        override val mediaId: String = id.toString()
    ) : LyricsUiState(id, mediaId)
}
