package com.mardous.booming.data.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class TextEncodingTest {
    @Test
    fun `repairs Spanish accents and artist names`() {
        assertEquals("jabón", "jab\u00C3\u00B3n".repairMojibake())
        assertEquals("José Muñoz — Canción", corrupt("José Muñoz — Canción").repairMojibake())
        assertEquals("ÁÉÍÓÚ áéíóú ñÑ üÜ ¿¡", corrupt("ÁÉÍÓÚ áéíóú ñÑ üÜ ¿¡", Charsets.ISO_8859_1).repairMojibake())
    }

    @Test
    fun `repairs Windows punctuation and supplementary characters`() {
        val text = "‘Hello’ — … € 🎵 🧼"
        assertEquals(text, corrupt(text).repairMojibake())
        assertEquals(text, corrupt(text, Charsets.ISO_8859_1).repairMojibake())
        assertEquals("“world”", corrupt("“world”", Charsets.ISO_8859_1).repairMojibake())
        assertEquals("”", "\u00E2\u20AC\u009D".repairMojibake())
    }

    @Test
    fun `repairs repeated encoding mistakes`() {
        val text = "jabón y canción 🎵"
        for (charset in listOf(Charsets.ISO_8859_1, windows1252)) {
            var broken = text
            repeat(3) {
                broken = corrupt(broken, charset)
                assertEquals(text, broken.repairMojibake())
            }
        }
    }

    @Test
    fun `preserves correct Unicode and legitimate Latin letters`() {
        val text = "jabón, Beyoncé, Sigur Rós, Ãngela, Ângelo, Île, Dvořák, Ólafur, Pâté, 東京, 中文, Привет, Ελληνικά, مرحبا, 🎵, e\u0301"
        assertSame(text, text.repairMojibake())
    }

    @Test
    fun `repairs only damaged spans in multilingual text`() {
        val text = "Beyoncé 東京 \uD83C\uDFB5 jab\u00C3\u00B3n, " + corrupt("niño") + " y e\u0301"
        assertEquals("Beyoncé 東京 🎵 jabón, niño y e\u0301", text.repairMojibake())
    }

    @Test
    fun `preserves incomplete and invalid sequences without inventing lost text`() {
        val text = "Ã Â â€ \u00C0\u00AF \u00ED\u00A0\u0080 \u00F4\u0090\u0080\u0080 jab�n"
        assertSame(text, text.repairMojibake())
    }

    @Test
    fun `strips a leading byte order mark without removing internal Unicode`() {
        assertEquals("jabón\uFEFF", "\uFEFFjabón\uFEFF".repairMojibake())
        assertEquals("jabón", corrupt("\uFEFFjabón").repairMojibake())
    }

    @Test
    fun `ASCII hot path reuses the original string`() {
        val text = "[00:12.34] A normal line\n[00:15.00] Next line"
        assertSame(text, text.repairMojibake())
        assertEquals(setOf(text), text.encodingVariants())
    }

    @Test
    fun `search variants include original corrected and double encoded names`() {
        val text = "José Muñoz"
        val variants = text.encodingVariants()
        assertTrue(variants.contains(text))
        assertTrue(variants.contains(corrupt(text)))
        assertTrue(variants.contains(corrupt(corrupt(text))))
        assertTrue(corrupt(text).encodingVariants().contains(text))
        assertTrue(variants.size <= 16)
    }

    @Test
    fun `recovers UTF8 scalar boundaries without accepting invalid Unicode`() {
        val points = listOf(0xA0, 0xFF, 0x100, 0x7FF, 0x800, 0xD7FF, 0xE000, 0xFFFF, 0x10000, 0x10FFFF)
        for (point in points) {
            val text = String(Character.toChars(point))
            assertEquals("U+${point.toString(16)}", text, corrupt(text, Charsets.ISO_8859_1).repairMojibake())
        }
    }

    private fun corrupt(text: String, charset: Charset = windows1252): String =
        String(text.toByteArray(Charsets.UTF_8), charset)

    companion object {
        private val windows1252 = Charset.forName("windows-1252")
    }
}
