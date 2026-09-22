package com.mardous.booming.data.local.lyrics

import com.mardous.booming.data.text.decodeStrictly
import com.mardous.booming.data.text.repairMojibake
import org.mozilla.universalchardet.UniversalDetector
import java.nio.charset.Charset

internal object LyricsFileDecoder {
    private const val DETECTION_LIMIT = 64 * 1024

    fun decode(bytes: ByteArray, forceUtf8: Boolean): String {
        val bomCharset = when {
            bytes.startsWith(0x00, 0x00, 0xFE, 0xFF) -> Charset.forName("UTF-32BE")
            bytes.startsWith(0xFF, 0xFE, 0x00, 0x00) -> Charset.forName("UTF-32LE")
            bytes.startsWith(0xFE, 0xFF) -> Charsets.UTF_16BE
            bytes.startsWith(0xFF, 0xFE) -> Charsets.UTF_16LE
            else -> null
        }
        val text = when {
            bomCharset != null -> bytes.toString(bomCharset)
            forceUtf8 -> bytes.toString(Charsets.UTF_8)
            else -> bytes.decodeStrictly(Charsets.UTF_8) ?: bytes.toString(detectCharset(bytes))
        }
        return text.repairMojibake()
    }

    private fun detectCharset(bytes: ByteArray): Charset {
        val detector = UniversalDetector()
        detector.handleData(bytes, 0, minOf(bytes.size, DETECTION_LIMIT))
        detector.dataEnd()
        return detector.detectedCharset?.let { Charset.forName(it) }
            ?: Charset.forName("windows-1252")
    }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { (this[it].toInt() and 0xFF) == prefix[it] }
}
