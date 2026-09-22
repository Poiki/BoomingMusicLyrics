package com.mardous.booming.data.local.artists

import com.mardous.booming.data.model.Artist
import com.mardous.booming.data.text.repairMojibake
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

internal fun String.cleanArtistName(): String =
    Normalizer.normalize(repairMojibake(), Normalizer.Form.NFC).trim().replace(artistSpaces, " ")

internal fun String.artistKey(): String = cleanArtistName().lowercase(Locale.ROOT)

private val artistSpaces = Regex("[\\s\\p{Z}]+")

internal class ArtistCredits(artistNames: List<String>, albumArtistNames: List<String>) {
    private val knownNames = (artistNames + albumArtistNames).distinct().flatMap(::explicitCredits)
        .filterNot { ambiguousSeparator.containsMatchIn(it) }
        .map { it.artistKey() }.toSet()
    private val parsed = HashMap<String, List<String>>()

    fun participants(value: String): List<String> = parsed.getOrPut(value) {
        explicitCredits(value).flatMap(::splitAmbiguousCredits)
            .distinctBy { it.artistKey() }
            .ifEmpty { listOf(Artist.UNKNOWN) }
    }

    private fun splitAmbiguousCredits(value: String): List<String> {
        val parts = value.split(ambiguousSeparator).map { it.cleanArtistName() }.filter { it.isNotEmpty() }
        // Punctuation alone is not enough to split band names such as AC/DC or Earth, Wind & Fire.
        return if (parts.size > 1 && parts.any { it.artistKey() in knownNames }) parts else listOf(value)
    }

    companion object {
        private val parenthesizedFeature = Regex("""(?i)\s*[\[(]\s*(?:feat(?:uring)?|ft|with|con)\.?\s+""")
        private val explicitSeparator = Regex("""(?i)[;\u0000\r\n]+|\s+(?:feat(?:uring)?|ft|with|con|vs)\.?\s+|\s+[x×]\s+""")
        private val ambiguousSeparator = Regex("""\s*[,/]\s*|\s+[&+]\s+""")

        private fun explicitCredits(value: String): List<String> {
            val cleaned = value.repairMojibake().trim()
            val text = if (parenthesizedFeature.containsMatchIn(cleaned)) {
                cleaned.replace(parenthesizedFeature, ";").trimEnd(')', ']', ' ')
            } else cleaned
            return text.split(explicitSeparator).map { it.cleanArtistName() }.filter { it.isNotEmpty() }
        }

        fun idForName(name: String): Long {
            val digest = MessageDigest.getInstance("SHA-256").digest(name.artistKey().toByteArray(Charsets.UTF_8))
            // Keep virtual IDs separate from MediaStore IDs and the -1/-2 sentinels.
            return (ByteBuffer.wrap(digest).long and 0x3FFF_FFFF_FFFF_FFFFL) or Long.MIN_VALUE
        }
    }
}
