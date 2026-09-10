package com.mardous.booming.playback.queue

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.mardous.booming.BuildConfig
import com.mardous.booming.coil.PlaybackArtworkStore
import com.mardous.booming.playback.lyrics.CarLyricsArtwork
import com.mardous.booming.playback.lyrics.CarLyricsArtworkCache
import com.mardous.booming.playback.lyrics.CarLyricsArtworkStore
import com.mardous.booming.playback.lyrics.CarLyricsMetadataPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
internal class CarQueueArtworkController(
    context: Context,
    private val player: Player,
    private val metadataPlayer: CarLyricsMetadataPlayer,
    private val scope: CoroutineScope
) : Player.Listener {
    private val applicationContext = context.applicationContext
    private val renderer = CarQueueArtworkRenderer(context)
    private val cache = CarLyricsArtworkCache<CarQueueCard, CarLyricsArtwork>(maxEntries = 2)
    private var enabled = false
    private var renderJob: Job? = null
    private var prefetchJob: Job? = null
    private var prefetchedCard: CarQueueCard? = null
    private var requestedCard: CarQueueCard? = null
    private var generation = 0L

    init {
        player.addListener(this)
    }

    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled
        refresh()
    }

    fun refresh() {
        renderJob?.cancel()
        prefetchJob?.cancel()
        prefetchedCard = null
        generation++
        requestedCard = null
        if (enabled) update()
    }

    fun release() {
        enabled = false
        renderJob?.cancel()
        prefetchJob?.cancel()
        player.removeListener(this)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (enabled && events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_MEDIA_METADATA_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED
            )) update()
    }

    private fun snapshot(startIndex: Int = player.currentMediaItemIndex): CarQueueCard? {
        val timeline = player.currentTimeline
        val indices = carQueueWindow(startIndex, timeline.windowCount, 5) {
            timeline.getNextWindowIndex(it, player.repeatMode, player.shuffleModeEnabled)
        }
        if (indices.isEmpty()) return null
        val window = Timeline.Window()
        val entries = indices.map { index ->
            val item = timeline.getWindow(index, window).mediaItem
            val metadata = if (index == player.currentMediaItemIndex) metadataPlayer.sourceMetadata else item.mediaMetadata
            CarQueueEntry(
                mediaId = item.mediaId,
                title = metadata.title?.toString() ?: metadata.displayTitle?.toString().orEmpty(),
                artist = metadata.artist?.toString().orEmpty()
            )
        }
        return CarQueueCard(
            entries,
            PlaybackArtworkStore.uriFor(
                if (startIndex == player.currentMediaItemIndex) metadataPlayer.sourceMetadata.artworkUri
                else timeline.getWindow(startIndex, window).mediaItem.mediaMetadata.artworkUri
            ),
            player.repeatMode == Player.REPEAT_MODE_ONE
        )
    }

    private fun update() {
        val card = snapshot()
        if (card == requestedCard) return
        requestedCard = card
        renderJob?.cancel()
        if (card == null || card != prefetchedCard) prefetchJob?.cancel()
        val revision = ++generation
        if (card == null) {
            metadataPlayer.clearMetadataOverride()
            return
        }
        cache.get(card)?.let {
            publish(card, revision, it)
            return
        }
        renderJob = scope.launch {
            // A burst of skips should render only the song that remains selected.
            delay(60)
            try {
                val artwork = cache.getOrRender(card) {
                    val cover = loadCover(card.coverUri)
                    withContext(Dispatchers.Default) { renderer.render(card, cover) }
                }
                ensureActive()
                if (enabled && generation == revision && requestedCard == card) {
                    publish(card, revision, artwork)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == revision) requestedCard = null
                Log.w("CarQueueArtwork", "Unable to render the upcoming queue", error)
            }
        }
    }

    private suspend fun loadCover(uri: Uri?): Bitmap? {
        if (uri == null) return null
        PlaybackArtworkStore.get(uri)?.let { return it.bitmap }
        return withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(applicationContext)
                    .data(uri)
                    .size(160)
                    .allowHardware(false)
                    .build()
                val result = SingletonImageLoader.get(applicationContext).execute(request)
                (result as? SuccessResult)?.image?.toBitmap()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("CarQueueArtwork", "Unable to load the current cover", error)
                null
            }
        }
    }

    private fun publish(card: CarQueueCard, revision: Long, artwork: CarLyricsArtwork) {
        val mediaId = card.entries.first().mediaId
        val uri = CarLyricsArtworkStore.publish(applicationContext, mediaId.toLongOrNull(), revision, artwork)
        metadataPlayer.setMetadataOverride(mediaId, uri)
        prefetchNext()
        if (BuildConfig.DEBUG) {
            Log.d("CarQueueArtwork", "Published queue: ${card.entries.map { it.mediaId }}, bytes=${artwork.data.size}")
        }
    }

    private fun prefetchNext() {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return
        val nextIndex = timeline.getNextWindowIndex(
            player.currentMediaItemIndex, player.repeatMode, player.shuffleModeEnabled
        )
        if (nextIndex == player.currentMediaItemIndex) return
        val next = snapshot(nextIndex) ?: return
        if (prefetchedCard == next && prefetchJob?.isCancelled == false) return
        prefetchJob?.cancel()
        prefetchedCard = next
        prefetchJob = scope.launch {
            // Reuse only an already prepared cover; this preview must never start a download.
            delay(250)
            val cover = next.coverUri?.let { PlaybackArtworkStore.get(it)?.bitmap } ?: return@launch
            try {
                cache.getOrRender(next) {
                    withContext(Dispatchers.Default) { renderer.render(next, cover) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("CarQueueArtwork", "Unable to prepare the next queue card", error)
            }
        }
    }
}

internal data class CarQueueEntry(val mediaId: String, val title: String, val artist: String)

internal data class CarQueueCard(
    val entries: List<CarQueueEntry>,
    val coverUri: Uri?,
    val repeatCurrent: Boolean
)
