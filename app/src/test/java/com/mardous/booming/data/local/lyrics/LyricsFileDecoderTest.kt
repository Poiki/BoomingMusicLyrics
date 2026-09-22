package com.mardous.booming.data.local.lyrics

import com.mardous.booming.data.local.lyrics.lrc.LrcLyricsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class LyricsFileDecoderTest {
    @Test
    fun `valid UTF8 wins over heuristic charset guesses`() {
        val text = "[00:01.00] jabón y niño, Beyoncé, 東京 🎵"
        for (force in listOf(false, true)) {
            assertEquals(text, LyricsFileDecoder.decode(text.toByteArray(), force))
        }
    }

    @Test
    fun `reads UTF8 with a byte order mark`() {
        assertEquals("jabón", LyricsFileDecoder.decode("\uFEFFjabón".toByteArray(), true))
    }

    @Test
    fun `recognizes Unicode byte order marks before decoding`() {
        val text = "[00:01.00] jabón 🎵"
        for (name in listOf("UTF-16LE", "UTF-16BE", "UTF-32LE", "UTF-32BE")) {
            val bytes = "\uFEFF$text".toByteArray(Charset.forName(name))
            for (force in listOf(false, true)) {
                assertEquals(name, text, LyricsFileDecoder.decode(bytes, force))
            }
        }
    }

    @Test
    fun `detects legacy Spanish lyrics without replacing accents`() {
        val text = "[00:01.00] jabón y corazón, niño, ¿dónde está la canción?\n[00:05.00] Él canta para mí"
        val bytes = text.toByteArray(Charset.forName("windows-1252"))
        assertEquals(text, LyricsFileDecoder.decode(bytes, false))
    }

    @Test
    fun `explicit UTF8 preference remains respected for legacy files`() {
        val bytes = byteArrayOf(0x6A, 0x61, 0x62, 0xF3.toByte(), 0x6E)
        assertTrue(LyricsFileDecoder.decode(bytes, true).contains('\uFFFD'))
    }

    @Test
    fun `repairs text already saved with the wrong encoding`() {
        val bytes = "[00:01.00] jab\u00C3\u00B3n".toByteArray()
        assertEquals("[00:01.00] jabón", LyricsFileDecoder.decode(bytes, true))
    }

    @Test
    fun `empty files remain empty`() {
        assertEquals("", LyricsFileDecoder.decode(byteArrayOf(), false))
    }

    @Test
    fun `LRC retains line and word timing after repairing shorter text`() {
        val text = "[00:01.00]<00:01.00>jab\u00C3\u00B3n <00:02.00>y <00:03.00>ni\u00C3\u00B1o\n[00:05.00]Adi\u00C3\u00B3s"
        val decoded = LyricsFileDecoder.decode(text.toByteArray(), true)
        val lyrics = LrcLyricsParser().parse(decoded, 10_000, false)!!
        val line = lyrics.lines.first { it.content.content.contains("jabón") }
        assertEquals(1_000L, line.start)
        assertEquals("jabón y niño", line.content.content)
        assertEquals(listOf(1_000L, 2_000L, 3_000L), line.content.syllables.map { it.start })
        assertEquals(listOf(0, 6, 8), line.content.syllables.map { it.startIndex })
        assertEquals(5_000L, lyrics.lines.first { it.content.content == "Adiós" }.start)
    }
}
