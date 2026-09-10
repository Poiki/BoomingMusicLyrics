package com.mardous.booming.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Player
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.mardous.booming.coil.PlaybackArtworkStore
import com.mardous.booming.coil.fetcher.AudioCoverFetcher
import com.mardous.booming.data.model.Song
import com.mardous.booming.data.repository.Repository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal class PlaybackArtworkPreloader(
    context: Context,
    private val player: Player,
    private val repository: Repository,
    private val scope: CoroutineScope
) : Player.Listener {
    private val applicationContext = context.applicationContext
    private var pendingUris = emptyList<Uri>()
    private var prefetchJob: Job? = null
    private var enabled = false

    init {
        player.addListener(this)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (enabled && events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED
            )) update()
    }

    fun enable() {
        if (enabled) return
        enabled = true
        update()
    }

    fun invalidate() {
        pendingUris = emptyList()
        prefetchJob?.cancel()
        if (enabled) update()
    }

    fun release() {
        prefetchJob?.cancel()
        player.removeListener(this)
        PlaybackArtworkStore.invalidate()
    }

    private fun update() {
        val candidates = listOf(
            player.currentMediaItemIndex,
            player.nextMediaItemIndex,
            player.previousMediaItemIndex
        ).filter { it != C.INDEX_UNSET && it in 0 until player.mediaItemCount }
            .distinct()
            .mapNotNull { index ->
                val item = player.getMediaItemAt(index)
                val uri = PlaybackArtworkStore.uriFor(item.mediaMetadata.artworkUri)
                if (uri != null && PlaybackArtworkStore.isPlaybackArtwork(uri)) uri to item else null
            }
        val uris = candidates.map { it.first }
        if (uris == pendingUris) return
        pendingUris = uris
        prefetchJob?.cancel()
        if (candidates.isEmpty()) return
        prefetchJob = scope.launch(Dispatchers.IO) {
            // Coalesce rapid skips and prepare only this small neighborhood in playback order.
            delay(100)
            for ((uri, item) in candidates) {
                ensureActive()
                if (PlaybackArtworkStore.get(uri) != null) continue
                try {
                    val song = repository.songByMediaItem(item, ignoreBlacklist = true)
                    if (song == Song.emptySong) continue
                    val request = ImageRequest.Builder(applicationContext)
                        .data(song)
                        .size(PlaybackArtworkStore.IMAGE_SIZE)
                        .allowHardware(false)
                        .apply { extras[AudioCoverFetcher.LOCAL_ONLY] = true }
                        .build()
                    val result = SingletonImageLoader.get(applicationContext).execute(request)
                    if (result !is SuccessResult) continue
                    ensureActive()
                    val artwork = PlaybackArtworkStore.encode(result.image.toBitmap())
                    ensureActive()
                    PlaybackArtworkStore.put(uri, artwork)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w("PlaybackArtwork", "Unable to prefetch $uri", error)
                }
            }
        }
    }
}
