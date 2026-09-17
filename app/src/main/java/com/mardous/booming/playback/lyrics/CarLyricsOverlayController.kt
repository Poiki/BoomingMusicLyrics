package com.mardous.booming.playback.lyrics

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.util.Log
import androidx.media3.common.Player
import com.mardous.booming.BuildConfig
import com.mardous.booming.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Keeps the lyrics artwork aligned with the playback clock without changing song text metadata. */
internal class CarLyricsOverlayController(
    context: Context,
    private val player: Player,
    private val metadataPlayer: CarLyricsMetadataPlayer,
    private val lyricsCoordinator: CurrentLyricsCoordinator,
    private val scope: CoroutineScope
) : Player.Listener {

    private val applicationContext = context.applicationContext
    private val handler = Handler(player.applicationLooper)
    private val messages = CarLyricsOverlayResolver.Messages(
        loading = context.getString(R.string.car_lyrics_loading),
        lyrics = context.getString(R.string.lyrics),
        notSynchronized = context.getString(R.string.car_lyrics_not_synchronized),
        instrumental = context.getString(R.string.car_lyrics_instrumental),
        notFound = context.getString(R.string.car_lyrics_not_found),
        error = context.getString(R.string.car_lyrics_error)
    )
    private val artworkRenderer = CarLyricsArtworkRenderer(context)
    private val artworkCache = CarLyricsArtworkCache<CarLyricsTextOverride, CarLyricsArtwork>()

    @Volatile
    private var modes = CarArtworkMode.COVER
    private val publicationGate = CarLyricsPublicationGate()
    private var renderedArtwork: RenderedArtwork? = null
    private val scheduledUpdate = Runnable(::renderAndSchedule)
    @Volatile
    private var stateJob: Job? = null
    private var artworkJob: Job? = null
    private var artworkPrefetchJob: Job? = null
    private var prefetchedArtworkKey: ArtworkKey? = null
    private var artworkGeneration = 0L

    init {
        player.addListener(this)
    }

    val isArtworkEnabled: Boolean
        get() = modes.artworkEnabled

    fun setModes(artworkEnabled: Boolean) {
        changeModes {
            when {
                artworkEnabled -> CarArtworkMode.LYRICS
                else -> CarArtworkMode.COVER
            }
        }
    }

    fun toggleArtwork(): Boolean = changeModes {
        it.toggle(CarArtworkMode.LYRICS)
    }.artworkEnabled

    fun release() {
        synchronized(this) {
            modes = CarArtworkMode.COVER
        }
        stateJob?.cancel()
        stateJob = null
        artworkJob?.cancel()
        artworkJob = null
        artworkPrefetchJob?.cancel()
        runOnPlayerThreadAndWait {
            handler.removeCallbacks(scheduledUpdate)
            player.removeListener(this)
            metadataPlayer.setModes(CarArtworkMode.COVER)
            clearPublishedOverlay(clearArtworkStore = true)
        }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (!modes.artworkEnabled) return
        if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED)) {
            artworkPrefetchJob?.cancel()
        }
        if (events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_MEDIA_METADATA_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY
            )) {
            renderAndSchedule()
        }
    }

    private fun changeModes(
        transform: (CarArtworkMode) -> CarArtworkMode
    ): CarArtworkMode {
        val previous: CarArtworkMode
        val updated: CarArtworkMode
        synchronized(this) {
            previous = modes
            updated = transform(previous)
            if (updated == previous) return updated
            modes = updated
        }
        Log.d(
            TAG,
            "Car artwork mode=$updated"
        )
        runOnPlayerThread {
            if (modes != updated) return@runOnPlayerThread
            handler.removeCallbacks(scheduledUpdate)
            clearPublishedOverlay()
            metadataPlayer.setModes(updated)
            if (updated.artworkEnabled) {
                ensureStateCollection()
                renderAndSchedule()
            } else {
                stopStateCollection()
            }
        }
        return updated
    }

    private fun ensureStateCollection() {
        if (stateJob?.isActive == true) return
        stateJob = scope.launch {
            lyricsCoordinator.lyricsUiState.collect {
                runOnPlayerThread(::renderAndSchedule)
            }
        }
    }

    private fun stopStateCollection() {
        stateJob?.cancel()
        stateJob = null
    }

    private fun renderAndSchedule() {
        handler.removeCallbacks(scheduledUpdate)
        val activeModes = modes
        if (!activeModes.artworkEnabled) return
        val mediaItem = player.currentMediaItem
        if (player.currentTimeline.isEmpty || mediaItem == null) {
            clearPublishedOverlay()
            return
        }

        val mediaId = mediaItem.mediaId
        val songId = lyricsCoordinator.songIdForMediaId(mediaId)
        val state = lyricsCoordinator.lyricsUiState.value
        val sourceMetadata = metadataPlayer.sourceMetadata
        val currentPositionMs = player.currentPosition.coerceAtLeast(0L)
        val presentation = CarLyricsOverlayResolver.resolve(
            state = state,
            currentMediaId = mediaId,
            positionMs = currentPositionMs,
            sourceTitle = sourceMetadata.title?.toString()
                ?: sourceMetadata.displayTitle?.toString(),
            messages = messages
        )
        applyOverlay(mediaId, songId, presentation, currentPositionMs, activeModes)
        if (activeModes.artworkEnabled &&
            renderedArtwork?.key == ArtworkKey(mediaId, presentation.primary, presentation.secondary)) {
            prefetchNextArtwork(mediaId, presentation)
        }

        val delayMs = CarLyricsOverlayResolver.nextDelayMs(
            nextUpdatePositionMs = presentation.nextUpdatePositionMs,
            currentPositionMs = currentPositionMs,
            isPlaying = player.isPlaying,
            playbackState = player.playbackState,
            speed = player.playbackParameters.speed
        ) ?: return
        if (delayMs == 0L) {
            handler.post(scheduledUpdate)
            return
        }
        handler.postDelayed(scheduledUpdate, delayMs)
    }

    private fun applyOverlay(
        mediaId: String,
        songId: Long?,
        presentation: CarLyricsOverlayResolver.Presentation,
        currentPositionMs: Long,
        activeModes: CarArtworkMode
    ) {
        val next = CarLyricsPublicationKey(
            mediaId = mediaId,
            primary = presentation.primary,
            secondary = presentation.secondary,
            modes = activeModes
        )
        if (!publicationGate.shouldPublish(next)) return

        val artworkKey = ArtworkKey(mediaId, presentation.primary, presentation.secondary)
        renderedArtwork?.takeIf { it.key == artworkKey }?.let { cached ->
            metadataPlayer.setMetadataOverride(mediaId, cached.uri)
            logPublication(presentation, currentPositionMs, activeModes, 0L, cached.byteCount)
            return
        }

        artworkJob?.cancel()
        val generation = ++artworkGeneration
        artworkCache.get(CarLyricsTextOverride(presentation.primary, presentation.secondary))?.let { cached ->
            val uri = CarLyricsArtworkStore.publish(applicationContext, songId, generation, cached)
            renderedArtwork = RenderedArtwork(artworkKey, uri, cached.data.size)
            metadataPlayer.setMetadataOverride(mediaId, uri)
            logPublication(presentation, currentPositionMs, activeModes, 0L, cached.data.size)
            prefetchNextArtwork(mediaId, presentation)
            return
        }
        // Retain the card for this song until its replacement is ready.
        metadataPlayer.setMetadataOverride(
            mediaId,
            renderedArtwork?.takeIf { it.key.mediaId == mediaId }?.uri
        )
        artworkJob = scope.launch {
            val renderStartedAtNs = System.nanoTime()
            val artworkData = try {
                renderArtwork(presentation)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.w(TAG, "Unable to render car lyrics artwork", error)
                null
            }
            val renderDurationMs = (System.nanoTime() - renderStartedAtNs) / NANOS_PER_MILLISECOND
            if (modes != activeModes ||
                artworkGeneration != generation ||
                publicationGate.current != next ||
                currentMediaIdentity() != mediaId
            ) {
                return@launch
            }
            val artworkUri = artworkData?.let {
                CarLyricsArtworkStore.publish(applicationContext, songId, generation, it)
            }
            if (artworkUri != null) {
                renderedArtwork = RenderedArtwork(artworkKey, artworkUri, artworkData.data.size)
                metadataPlayer.setMetadataOverride(mediaId, artworkUri)
                prefetchNextArtwork(mediaId, presentation)
            }
            logPublication(
                presentation,
                currentPositionMs,
                activeModes,
                renderDurationMs,
                artworkData?.data?.size
            )
        }
    }

    private suspend fun renderArtwork(presentation: CarLyricsOverlayResolver.Presentation): CarLyricsArtwork =
        artworkCache.getOrRender(CarLyricsTextOverride(presentation.primary, presentation.secondary)) {
            withContext(Dispatchers.Default) {
                artworkRenderer.render(presentation.primary, presentation.secondary)
            }
        }

    private fun prefetchNextArtwork(mediaId: String, presentation: CarLyricsOverlayResolver.Presentation) {
        val positionMs = presentation.nextUpdatePositionMs ?: return
        if (!player.isPlaying) return
        val next = CarLyricsOverlayResolver.resolve(
            lyricsCoordinator.lyricsUiState.value,
            mediaId,
            positionMs,
            metadataPlayer.sourceMetadata.title?.toString(),
            messages
        )
        val key = ArtworkKey(mediaId, next.primary, next.secondary)
        if (prefetchedArtworkKey == key && artworkPrefetchJob?.isCancelled == false) return
        artworkPrefetchJob?.cancel()
        prefetchedArtworkKey = key
        artworkPrefetchJob = scope.launch {
            try {
                renderArtwork(next)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Unable to pre-render car lyrics artwork", error)
            }
        }
    }

    private fun cancelArtworkRender() {
        artworkJob?.cancel()
        artworkJob = null
        artworkGeneration++
    }

    private fun clearPublishedOverlay(clearArtworkStore: Boolean = false) {
        cancelArtworkRender()
        artworkPrefetchJob?.cancel()
        publicationGate.reset()
        renderedArtwork = null
        metadataPlayer.clearMetadataOverride()
        if (clearArtworkStore) CarLyricsArtworkStore.clear()
    }

    private fun logPublication(
        presentation: CarLyricsOverlayResolver.Presentation,
        scheduledPositionMs: Long,
        activeModes: CarArtworkMode,
        artworkRenderDurationMs: Long?,
        artworkByteCount: Int?
    ) {
        if (!BuildConfig.DEBUG || presentation.lineIndex == null) return
        val publishedPositionMs = player.currentPosition.coerceAtLeast(0L)
        val lineStartPositionMs = presentation.lineStartPositionMs ?: scheduledPositionMs
        Log.d(
            TAG,
            "lineIndex=${presentation.lineIndex}, " +
                    "lineStartMs=$lineStartPositionMs, " +
                    "nextUpdateMs=${presentation.nextUpdatePositionMs}, " +
                    "positionMs=$publishedPositionMs, " +
                    "driftMs=${publishedPositionMs - lineStartPositionMs}, " +
                    "artworkMode=${activeModes.artworkEnabled}, " +
                    "artworkRenderMs=${artworkRenderDurationMs ?: "none"}, " +
                    "artworkBytes=${artworkByteCount ?: 0}"
        )
    }

    private fun currentMediaIdentity(): String? = player.currentMediaItem?.mediaId

    private fun runOnPlayerThread(block: () -> Unit) {
        if (handler.looper.isCurrentThread) block() else handler.post(block)
    }

    private fun runOnPlayerThreadAndWait(block: () -> Unit) {
        if (handler.looper.isCurrentThread) {
            block()
            return
        }
        val completion = CountDownLatch(1)
        handler.post {
            try {
                block()
            } finally {
                completion.countDown()
            }
        }
        check(completion.await(RELEASE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "Timed out releasing car lyrics overlay"
        }
    }

    private data class ArtworkKey(
        val mediaId: String,
        val primary: String,
        val secondary: String?
    )

    private data class RenderedArtwork(
        val key: ArtworkKey,
        val uri: Uri,
        val byteCount: Int
    )

    private companion object {
        const val TAG = "CarLyricsOverlay"
        const val RELEASE_TIMEOUT_SECONDS = 5L
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
