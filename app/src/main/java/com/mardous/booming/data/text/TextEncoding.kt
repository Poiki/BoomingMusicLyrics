package com.mardous.booming.data.text

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset

private const val MAX_REPAIR_PASSES = 3
private val windows1252 = Charset.forName("windows-1252")

/** Repairs complete UTF-8 sequences that were read as Latin-1 or Windows-1252. */
fun String.repairMojibake(): String {
    var result = this
    repeat(MAX_REPAIR_PASSES) {
        val repaired = result.repairPass()
        if (repaired === result) return result.removePrefix("\uFEFF")
        result = repaired
    }
    return result.removePrefix("\uFEFF")
}

private fun String.repairPass(): String {
    var output: StringBuilder? = null
    var index = 0
    while (index < length) {
        val first = this[index].code
        val count = when (first) {
            in 0xC2..0xDF -> 2
            in 0xE0..0xEF -> 3
            in 0xF0..0xF4 -> 4
            else -> 0
        }
        var codePoint = first and (0x7F shr count)
        var valid = count != 0 && index + count <= length
        if (valid) {
            for (offset in 1 until count) {
                val next = this[index + offset].legacyByte()
                if (next !in 0x80..0xBF) {
                    valid = false
                    break
                }
                codePoint = (codePoint shl 6) or (next and 0x3F)
            }
        }
        // Reject overlong encodings, surrogates and values outside Unicode.
        val minimum = when (count) {
            2 -> 0x80
            3 -> 0x800
            else -> 0x10000
        }
        if (valid && codePoint >= minimum && codePoint <= 0x10FFFF &&
            codePoint !in 0xD800..0xDFFF && codePoint != 0xFFFD
        ) {
            if (output == null) output = StringBuilder(length).append(this, 0, index)
            output.appendCodePoint(codePoint)
            index += count
        } else {
            output?.append(this[index])
            index++
        }
    }
    return output?.toString() ?: this
}

private fun Char.legacyByte(): Int = when (this) {
    '\u20AC' -> 0x80
    '\u201A' -> 0x82
    '\u0192' -> 0x83
    '\u201E' -> 0x84
    '\u2026' -> 0x85
    '\u2020' -> 0x86
    '\u2021' -> 0x87
    '\u02C6' -> 0x88
    '\u2030' -> 0x89
    '\u0160' -> 0x8A
    '\u2039' -> 0x8B
    '\u0152' -> 0x8C
    '\u017D' -> 0x8E
    '\u2018' -> 0x91
    '\u2019' -> 0x92
    '\u201C' -> 0x93
    '\u201D' -> 0x94
    '\u2022' -> 0x95
    '\u2013' -> 0x96
    '\u2014' -> 0x97
    '\u02DC' -> 0x98
    '\u2122' -> 0x99
    '\u0161' -> 0x9A
    '\u203A' -> 0x9B
    '\u0153' -> 0x9C
    '\u017E' -> 0x9E
    '\u0178' -> 0x9F
    else -> code.takeIf { it <= 0xFF } ?: -1
}

internal fun ByteArray.decodeStrictly(charset: Charset): String? = try {
    charset.newDecoder().decode(ByteBuffer.wrap(this)).toString()
} catch (_: CharacterCodingException) {
    null
}

/** Keeps queries compatible with unchanged metadata in MediaStore. */
internal fun String.encodingVariants(): Set<String> {
    val variants = linkedSetOf(this, repairMojibake())
    var previous = variants.toList()
    repeat(MAX_REPAIR_PASSES) {
        val next = mutableListOf<String>()
        for (value in previous) {
            if (value.all { it.code < 0x80 }) continue
            val utf8 = value.toByteArray(Charsets.UTF_8)
            for (charset in arrayOf(Charsets.ISO_8859_1, windows1252)) {
                val encoded = utf8.decodeStrictly(charset) ?: continue
                if (variants.add(encoded)) next.add(encoded)
            }
        }
        previous = next
    }
    return variants
}
