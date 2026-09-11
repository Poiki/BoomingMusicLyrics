package com.mardous.booming.playback.library

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import com.mardous.booming.R
import com.mardous.booming.coil.PlaybackArtworkStore
import com.mardous.booming.playback.queue.CarQueueReferences
import com.mardous.booming.playback.queue.carQueuePage

/** Main-thread browser snapshots, sharing the player's metadata and cached artwork. */
@OptIn(UnstableApi::class)
internal class CarQueueBrowser(private val context: Context, private val player: Player) {
    private data class Cursor(val startId: String, val stopId: String)

    private val token = System.nanoTime().toString(36) + ":"
    private val references = CarQueueReferences<Any>(ITEM_PREFIX + token)
    private val pages = LinkedHashMap<String, Cursor>(16, 0.75f, true)
    private val snapshots = HashMap<String, List<MediaItem>>()
    private val window = Timeline.Window()
    private var pageRevision = 0L

    val root = folder(MediaIDs.CAR_UP_NEXT, R.string.up_next, R.drawable.ic_queue_music_24dp)
    val library = folder(MediaIDs.CAR_LIBRARY, R.string.library_title, R.drawable.ic_library_music_24dp)

    fun isQueueParent(id: String) = id == MediaIDs.CAR_UP_NEXT || id.startsWith(PAGE_PREFIX)

    fun invalidate() = snapshots.clear()

    fun item(id: String): MediaItem? = when {
        id == MediaIDs.CAR_UP_NEXT -> root
        id == MediaIDs.CAR_LIBRARY -> library
        id.startsWith(PAGE_PREFIX) && pages.containsKey(id) -> moreFolder(id)
        isQueueItem(id) -> resolve(id)?.let { row(it) }
        else -> null
    }

    fun children(parentId: String): List<MediaItem> {
        if (parentId != MediaIDs.CAR_UP_NEXT && !pages.containsKey(parentId)) return emptyList()
        return snapshots.getOrPut(parentId) { buildPage(parentId) }
    }

    private fun buildPage(parentId: String): List<MediaItem> {
        val timeline = player.currentTimeline
        val currentIndex = player.currentMediaItemIndex
        if (timeline.isEmpty || currentIndex !in 0 until timeline.windowCount) {
            return emptyList()
        }
        val cursor = if (parentId == MediaIDs.CAR_UP_NEXT) null else {
            pages[parentId] ?: return emptyList()
        }
        val start = cursor?.let { resolve(it.startId) ?: return emptyList() } ?: currentIndex
        val stop = cursor?.let { resolve(it.stopId) ?: return emptyList() } ?: currentIndex
        val page = carQueuePage(start, stop, timeline.windowCount, PAGE_SIZE) { index ->
            timeline.getNextWindowIndex(index, player.repeatMode, player.shuffleModeEnabled)
        }
        return buildList {
            page.indices.forEach { add(row(it)) }
            page.nextIndex?.let { next ->
                val nextCursor = Cursor(reference(next), reference(stop))
                val id = pages.entries.firstOrNull { it.value == nextCursor }?.key
                    ?: (PAGE_PREFIX + token + (++pageRevision).toString(36)).also {
                        pages[it] = nextCursor
                        if (pages.size > MAX_PAGES) {
                            val oldest = pages.keys.first()
                            pages.remove(oldest)
                            snapshots.remove(oldest)
                        }
                    }
                add(moreFolder(id))
            }
        }
    }

    fun select(id: String, positionMs: Long): Boolean {
        val index = resolve(id) ?: return false
        if (index != player.currentMediaItemIndex || positionMs != C.TIME_UNSET) {
            player.seekTo(index, positionMs)
        }
        return true
    }

    private fun resolve(id: String): Int? = references.resolve(id, player.mediaItemCount) {
        player.currentTimeline.getWindow(it, window).uid
    }

    private fun reference(index: Int): String = references.register(
        player.currentTimeline.getWindow(index, window).uid, index
    )

    private fun row(index: Int): MediaItem {
        val item = player.getMediaItemAt(index)
        val metadata = item.mediaMetadata
        return MediaItem.Builder()
            .setMediaId(reference(index))
            .setMediaMetadata(
                metadata.buildUpon()
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setArtworkData(null, null)
                    .setArtworkUri(PlaybackArtworkStore.uriFor(metadata.artworkUri))
                    .setExtras(Bundle(metadata.extras ?: Bundle.EMPTY).apply {
                        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM,
                            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
                        putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                            context.getString(if (index == player.currentMediaItemIndex) {
                                R.string.now_playing
                            } else {
                                R.string.up_next
                            }))
                    })
                    .build()
            )
            .build()
    }

    private fun moreFolder(id: String) = folder(id, R.string.car_queue_more, R.drawable.ic_next_24dp)

    private fun folder(id: String, title: Int, icon: Int) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(context.getString(title))
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setArtworkUri(Uri.parse("android.resource://${context.packageName}/$icon"))
                .setExtras(Bundle().apply {
                    putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
                    putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
                })
                .build()
        )
        .build()

    companion object {
        private const val PAGE_SIZE = 40
        private const val MAX_PAGES = 8
        private const val ITEM_PREFIX = "CAR_QUEUE_ITEM:"
        private const val PAGE_PREFIX = "CAR_QUEUE_PAGE:"

        fun isQueueItem(id: String) = id.startsWith(ITEM_PREFIX)
    }
}
