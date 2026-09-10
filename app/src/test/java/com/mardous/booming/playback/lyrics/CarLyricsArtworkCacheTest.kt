package com.mardous.booming.playback.lyrics

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CarLyricsArtworkCacheTest {
    @Test
    fun `publication shares a pending pre-render instead of rendering twice`() = runTest {
        val cache = CarLyricsArtworkCache<String, String>()
        val rendered = CompletableDeferred<String>()
        var renders = 0
        val prefetch = async {
            cache.getOrRender("line") { renders++; rendered.await() }
        }
        runCurrent()
        val publication = async {
            cache.getOrRender("line") { renders++; "duplicate" }
        }
        runCurrent()
        assertFalse(publication.isCompleted)
        assertNull(cache.get("line"))
        rendered.complete("card")

        assertEquals("card", prefetch.await())
        assertEquals("card", publication.await())
        assertEquals(1, renders)
        assertEquals("card", cache.get("line"))
    }

    @Test
    fun `cancelling an obsolete pre-render releases the next song`() = runTest {
        val cache = CarLyricsArtworkCache<String, String>()
        val obsolete = async {
            cache.getOrRender("old") { CompletableDeferred<String>().await() }
        }
        runCurrent()
        val current = async { cache.getOrRender("new") { "current card" } }
        obsolete.cancelAndJoin()

        assertEquals("current card", current.await())
        assertEquals("retry card", cache.getOrRender("old") { "retry card" })
    }

    @Test
    fun `cache bounds memory and keeps recently reused lines`() = runTest {
        val cache = CarLyricsArtworkCache<String, String>(maxEntries = 2)
        cache.getOrRender("first") { "first card" }
        cache.getOrRender("second") { "second card" }
        cache.getOrRender("first") { error("Cached line must be reused") }
        cache.getOrRender("third") { "third card" }

        assertEquals("first card", cache.getOrRender("first") { "unexpected render" })
        assertEquals("re-rendered second", cache.getOrRender("second") { "re-rendered second" })
    }

    @Test
    fun `failed renders are not cached and do not block later artwork`() = runTest {
        val cache = CarLyricsArtworkCache<String, String>()
        runCatching { cache.getOrRender("line") { error("Render failure") } }

        assertEquals("recovered", cache.getOrRender("line") { "recovered" })
    }
}
