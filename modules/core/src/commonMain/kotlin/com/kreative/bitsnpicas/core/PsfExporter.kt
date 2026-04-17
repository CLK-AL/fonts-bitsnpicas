package com.kreative.bitsnpicas.core

/**
 * Pure-Kotlin multiplatform PSF v2 bitmap font writer.
 *
 * Ported from the frozen Java `PSFBitmapFontExporter.exportFontImpl`
 * (default constructor path: version=2, useAllGlyphs=true,
 * unicodeTable=true, no GlyphList encoding, no gzip).
 *
 * Produces a PSF v2 binary:
 *   - 32-byte header (magic, version=0, headerSize=32, flags,
 *     numGlyphs, charSize, height, width)
 *   - per-glyph bitmap data (row-major, MSB-first, byte-padded)
 *   - optional Unicode table (UTF-8 entries, 0xFF terminator)
 *
 * The round-trip [PsfImporter.read] -> [PsfExporter.write] -> [PsfImporter.read]
 * is semantically stable: glyph count, dimensions, advance, offsets,
 * and bitmap bytes survive intact.
 */
public object PsfExporter {

    /**
     * Serialize [font] to PSF v2 binary format.
     *
     * @param includeUnicodeTable whether to append a Unicode mapping table
     *        (default true, matching Java default constructor).
     */
    public fun write(font: BitmapFont, includeUnicodeTable: Boolean = true): ByteArray {
        // Collect codepoints, sorted for deterministic output.
        val codePoints = font.glyphs.keys.sorted()

        val ascent = font.lineAscent
        val h = ascent + font.lineDescent + font.lineGap
        val w = getMaxWidth(font, codePoints)

        val bytesPerRow = (w + 7) / 8
        val charSize = h * bytesPerRow

        val out = ByteWriter(32 + codePoints.size * charSize + codePoints.size * 8)

        // PSF v2 header -- all multi-byte fields are little-endian
        // except the magic which is written as a big-endian 4-byte sequence.
        // Magic: 0x72 0xB5 0x4A 0x86
        out.writeU8(0x72)
        out.writeU8(0xB5)
        out.writeU8(0x4A)
        out.writeU8(0x86)
        // Version: 0 (32-bit LE)
        out.writeIntLE(0)
        // Header size: 32 (32-bit LE)
        out.writeIntLE(32)
        // Flags: bit 0 = has unicode table
        out.writeIntLE(if (includeUnicodeTable) 1 else 0)
        // Number of glyphs
        out.writeIntLE(codePoints.size)
        // Char size (bytes per glyph bitmap)
        out.writeIntLE(charSize)
        // Height
        out.writeIntLE(h)
        // Width
        out.writeIntLE(w)

        // Bitmap data
        for (cp in codePoints) {
            val g = font.glyphs[cp]
            writeGlyph(out, w, h, ascent, g)
        }

        // Unicode table
        if (includeUnicodeTable) {
            for (cp in codePoints) {
                if (cp >= 0) {
                    // Write the codepoint as UTF-8
                    val utf8 = codePointToUtf8(cp)
                    out.writeBytes(utf8)
                }
                // Terminator
                out.writeU8(0xFF)
            }
        }

        return out.toByteArray()
    }

    private fun getMaxWidth(font: BitmapFont, codePoints: List<Int>): Int {
        var maxWidth = 0
        for (cp in codePoints) {
            val g = font.glyphs[cp] ?: continue
            if (g.advance > maxWidth) maxWidth = g.advance
        }
        return maxWidth
    }

    private fun writeGlyph(out: ByteWriter, w: Int, h: Int, ascent: Int, g: BitmapGlyph?) {
        if (g == null) {
            // Empty glyph: all zero bytes
            for (y in 0 until h) {
                for (x in 0 until w step 8) {
                    out.writeU8(0)
                }
            }
            return
        }

        for (y in 0 until h) {
            val j = y - (ascent - g.y)  // row index into glyph bitmap
            if (j >= 0 && j < g.bitmap.size) {
                var i = -g.x  // column index into glyph bitmap
                var x = 0
                while (x < w) {
                    var b = 0
                    var m = 0x80
                    while (m != 0 && x < w) {
                        if (i >= 0 && i < g.bitmap[j].size) {
                            if ((g.bitmap[j][i] and 0x80) != 0) b = b or m
                        }
                        x++
                        i++
                        m = m shr 1
                    }
                    out.writeU8(b)
                }
            } else {
                // Row outside glyph: all zero bytes
                for (x in 0 until w step 8) {
                    out.writeU8(0)
                }
            }
        }
    }

    /**
     * Encode a Unicode codepoint to UTF-8 bytes.
     */
    private fun codePointToUtf8(cp: Int): ByteArray {
        return when {
            cp < 0x80 -> byteArrayOf(cp.toByte())
            cp < 0x800 -> byteArrayOf(
                (0xC0 or (cp shr 6)).toByte(),
                (0x80 or (cp and 0x3F)).toByte(),
            )
            cp < 0x10000 -> byteArrayOf(
                (0xE0 or (cp shr 12)).toByte(),
                (0x80 or ((cp shr 6) and 0x3F)).toByte(),
                (0x80 or (cp and 0x3F)).toByte(),
            )
            else -> byteArrayOf(
                (0xF0 or (cp shr 18)).toByte(),
                (0x80 or ((cp shr 12) and 0x3F)).toByte(),
                (0x80 or ((cp shr 6) and 0x3F)).toByte(),
                (0x80 or (cp and 0x3F)).toByte(),
            )
        }
    }
}
