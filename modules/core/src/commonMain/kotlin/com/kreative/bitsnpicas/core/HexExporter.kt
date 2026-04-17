package com.kreative.bitsnpicas.core

/**
 * Pure-Kotlin multiplatform Unifont Hex bitmap font writer.
 *
 * Ported from the frozen Java `HexBitmapFontExporter.exportFont`.
 * Each output line is `CODEPOINT:HEXBITMAPDATA` where the codepoint
 * is a 4- or 6-digit uppercase hex integer and the bitmap data is
 * a hex string encoding 1-bit glyph rows.
 *
 * Like the BDF exporter, this port iterates [BitmapFont.glyphs]
 * directly (O(#glyphs)), never 0..0x110000 -- M7 fix carried forward.
 *
 * The round-trip [HexImporter.read] -> [HexExporter.write] -> [HexImporter.read]
 * is semantically stable.
 */
public object HexExporter {

    /**
     * Serialize [font] to Hex text format.
     */
    public fun write(font: BitmapFont): String {
        val sb = StringBuilder()
        val h = (font.lineAscent + font.lineDescent + 4) / 8

        // Sorted by codepoint for deterministic output.
        val sortedCps = font.glyphs.keys.sorted()

        for (cp in sortedCps) {
            val g = font.glyphs[cp] ?: continue

            // Width in hex nibbles
            var width = (g.advance + 3) / 4
            if (width <= 0) continue
            if (width < h * 2 - 2) width = h * 2 - 2
            if (width > h * 2 + 1) width = h * 2 + 1

            val hex = StringBuilder()
            for (j in 0 until h * 8) {
                val y = j - (g.y - h * 7)
                var x = -g.x
                for (i in 0 until width) {
                    var b = 0
                    for (m in intArrayOf(8, 4, 2, 1)) {
                        if (y >= 0 && y < g.bitmap.size &&
                            x >= 0 && x < g.bitmap[y].size &&
                            (g.bitmap[y][x].toInt() and 0x80) != 0
                        ) {
                            b = b or m
                        }
                        x++
                    }
                    hex.append(hexDigit(b))
                }
            }

            // Format codepoint: 4 or 6 hex digits
            var cps = toHexUpper(cp)
            while (cps.length != 4 && cps.length != 6) cps = "0$cps"

            sb.append(cps).append(':').append(hex).append('\n')
        }
        return sb.toString()
    }

    private fun toHexUpper(value: Int): String {
        if (value == 0) return "0"
        val buf = StringBuilder()
        var v = value
        while (v != 0) {
            buf.append(hexDigit(v and 0x0F))
            v = v ushr 4
        }
        return buf.reverse().toString()
    }

    private fun hexDigit(nibble: Int): Char {
        return if (nibble < 10) ('0' + nibble) else ('A' + (nibble - 10))
    }
}
