package com.kreative.bitsnpicas.core

import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Pure-Kotlin multiplatform Unifont Hex bitmap font parser.
 *
 * Ported from the frozen Java `HexBitmapFontImporter`.
 * Each line of the input is `CODEPOINT:HEXBITMAPDATA` where
 * the codepoint is a hex integer and the bitmap data is a
 * hex string encoding the 1-bit glyph row-by-row.
 *
 * Height is derived from the hex-data length via:
 *   h = floor(sqrt(len + 4) + 2) / 4
 *   height = h * 8
 *   width  = len / height   (in hex nibbles)
 *
 * Carries m11 fix: guards against division-by-zero when
 * the derived height is zero (zero-length hex data).
 */
public object HexImporter {

    /**
     * Exception thrown when Hex data is malformed beyond recovery.
     */
    public class HexParseException(message: String) : ParseException(message)

    /**
     * Parse a Hex bitmap font from its text content.
     *
     * @param text the full `.hex` file content as a string.
     * @return the parsed [BitmapFont]; empty font if no valid lines found.
     */
    public fun read(text: String): BitmapFont {
        var maxh = 1
        val glyphs = mutableMapOf<Int, BitmapGlyph>()

        for (line in text.lineSequence()) {
            val fields = line.split(":")
            if (fields.size != 2) continue

            val cp = fields[0].trim().toIntOrNull(16) ?: continue
            val hexStr = fields[1].trim()
            val hex = hexStr.toCharArray()

            val h = (floor(sqrt((hex.size + 4).toDouble())).toInt() + 2) / 4

            // m11 fix: guard against height <= 0 (zero-length hex data)
            if (h <= 0) continue

            if (h > maxh) maxh = h
            val height = h * 8
            val width = hex.size / height

            // Guard: width < 1 means hex data is too short for derived height
            if (width < 1) continue

            val rows = MutableList(height) { IntArray(width * 4) }
            var i = 0
            var valid = true
            for (y in 0 until height) {
                var x = 0
                for (j in 0 until width) {
                    val b = hexDigitValue(hex[i++])
                    if (b < 0) { valid = false; break }
                    rows[y][x++] = if ((b and 8) != 0) 0xFF else 0
                    rows[y][x++] = if ((b and 4) != 0) 0xFF else 0
                    rows[y][x++] = if ((b and 2) != 0) 0xFF else 0
                    rows[y][x++] = if ((b and 1) != 0) 0xFF else 0
                }
                if (!valid) break
            }
            if (!valid) continue

            val glyph = BitmapGlyph(
                bitmap = rows,
                x = 0,
                advance = width * 4,
                y = h * 7,
            )
            glyphs[cp] = glyph
        }

        return BitmapFont(
            glyphs = glyphs,
            emAscent = maxh * 7,
            emDescent = maxh,
            lineAscent = maxh * 7,
            lineDescent = maxh,
            xHeight = if (maxh > 1) maxh * 4 else 5,
            capHeight = if (maxh > 1) maxh * 5 else 7,
            lineGap = 0,
            newGlyphWidth = maxh * 8,
        )
    }

    /**
     * Return the numeric value (0-15) of a hex digit character,
     * or -1 if the character is not a valid hex digit.
     */
    private fun hexDigitValue(c: Char): Int {
        return when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> -1
        }
    }
}
