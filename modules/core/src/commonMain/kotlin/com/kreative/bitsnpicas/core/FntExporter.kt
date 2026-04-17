package com.kreative.bitsnpicas.core

/**
 * Pure-Kotlin multiplatform Windows FNT v2 bitmap font writer.
 *
 * Ported from the frozen Java `FNTBitmapFontExporter.exportFontImpl`
 * (default constructor: magic=3, no GlyphList encoding => CP1252).
 *
 * Produces a Windows FNT binary:
 *   - Fixed-size header (118 bytes for v2, 148 bytes for v3)
 *   - Per-character width + offset table
 *   - Bitmap data block (column-major within each glyph, matching FNT spec)
 *   - Face name (null-terminated CP1252 string)
 *
 * The round-trip [FntImporter.read] -> [FntExporter.write] -> [FntImporter.read]
 * is semantically stable.
 */
public object FntExporter {

    /**
     * Serialize [font] to Windows FNT v2 binary format.
     *
     * @param version FNT version to write (2 or 3, default 2 for
     *        maximum compatibility and simpler char table entries).
     */
    public fun write(font: BitmapFont, version: Int = 2): ByteArray {
        require(version in 1..3) { "FNT version must be 1, 2, or 3" }

        // Vertical metrics
        val ascent = font.lineAscent
        val height = ascent + font.lineDescent
        val points = font.emAscent + font.emDescent
        val leading = font.lineGap

        // Character set: we always use CP1252 (charSet=0) since
        // commonMain doesn't carry GlyphList encoding.
        val charSet = 0

        // Horizontal metrics and bitmaps -- scan glyphs via CP1252 mapping
        var maxWidth = 0
        var avgWidth = 0
        var numChars = 0
        var firstChar = -1
        var lastChar = -1
        var defaultChar = -1
        var breakChar = -1
        var widthBytes = 0
        val widths = mutableMapOf<Int, Int>()
        val bitmaps = mutableMapOf<Int, ByteArray>()

        for (idx in 0 until 256) {
            val ch = fromCP1252(idx)
            val cp = if (ch < 0) 0xF000 + idx else ch
            val g = font.glyphs[cp] ?: continue

            val glyphWidth = g.advance
            if (glyphWidth > maxWidth) maxWidth = glyphWidth
            avgWidth += glyphWidth
            numChars++
            if (cp == 32) breakChar = idx
            if (firstChar < 0) firstChar = idx
            lastChar = idx

            val rowBytes = (glyphWidth + 7) / 8
            widthBytes += rowBytes
            val data = ByteArray(rowBytes * height)

            // Rasterize glyph into column-major FNT format
            for (y in 0 until height) {
                val j = y - (ascent - g.y)  // row index into glyph bitmap
                if (j >= 0 && j < g.bitmap.size) {
                    var dy = y
                    var i = -g.x  // column index into glyph bitmap
                    var x = 0
                    while (x < glyphWidth) {
                        var m = 0x80
                        while (m != 0 && x < glyphWidth) {
                            if (i >= 0 && i < g.bitmap[j].size) {
                                if ((g.bitmap[j][i] and 0x80) != 0) {
                                    data[dy] = (data[dy].toInt() or m).toByte()
                                }
                            }
                            x++
                            i++
                            m = m shr 1
                        }
                        dy += height
                    }
                }
            }

            widths[idx] = glyphWidth
            bitmaps[idx] = data
        }

        // Handle empty font: ensure we have at least one character
        if (numChars == 0) {
            firstChar = 0
            lastChar = 0
            numChars = 1
            avgWidth = 1
            widths[0] = 1
            bitmaps[0] = ByteArray(((1 + 7) / 8) * height)
            widthBytes = (1 + 7) / 8
        }

        // Create notdef bitmap
        if (numChars > 0) avgWidth /= numChars
        if (avgWidth < 1) avgWidth = 1
        val notdefRowBytes = (avgWidth + 7) / 8
        val notdefData = ByteArray(notdefRowBytes * height)
        for (y in 0 until height) {
            if (y > 0 && y < height - 1) {
                var dy = y
                var x = 0
                while (x < avgWidth) {
                    var m = 0x80
                    while (m != 0 && x < avgWidth) {
                        if (x > 0) {
                            notdefData[dy] = (notdefData[dy].toInt() or m).toByte()
                        }
                        x++
                        m = m shr 1
                    }
                    dy += height
                }
            }
        }

        // Fill gaps and add notdef bitmaps
        var isMono = true
        for (idx in firstChar..lastChar) {
            if (widths.containsKey(idx)) {
                if (widths[idx] != avgWidth) {
                    isMono = false
                }
            } else {
                if (defaultChar < 0) defaultChar = idx
                widthBytes += notdefRowBytes
                numChars++
                widths[idx] = avgWidth
                bitmaps[idx] = notdefData
            }
        }

        // Add absolute space bitmap (one extra entry after lastChar)
        if (breakChar < 0) breakChar = lastChar + 1
        if (defaultChar < 0) defaultChar = lastChar + 1
        widthBytes += notdefRowBytes
        numChars++
        widths[lastChar + 1] = avgWidth
        bitmaps[lastChar + 1] = ByteArray(notdefRowBytes * height)

        // Sizes and offsets
        val charTableEntrySize = if (version >= 3) 6 else 4
        val charTableSize = numChars * charTableEntrySize
        val headerSize = if (version >= 3) 148 else 118
        var bitsOffset = charTableSize + headerSize
        val face = bitsOffset + (widthBytes * height)
        val faceBytes = encodeCp1252(font.name ?: "")
        val size = face + faceBytes.size + 1

        val out = ByteWriter(size)

        // Write header

        // magic (version): 2 bytes big-endian (DataOutputStream.writeShort is BE)
        out.writeU16BE(version)
        // size: 4 bytes little-endian
        out.writeIntLE(size)
        // copyright: 60 bytes (we leave it empty)
        out.writeZeros(60)
        // type: 16-bit LE
        out.writeU16LE(0)
        // points: 16-bit LE
        out.writeU16LE(points)
        // vertRes: 16-bit LE
        out.writeU16LE(96)
        // horizRes: 16-bit LE
        out.writeU16LE(96)
        // ascent: 16-bit LE
        out.writeU16LE(ascent)
        // internalLeading: 16-bit LE
        out.writeU16LE(0)
        // externalLeading: 16-bit LE
        out.writeU16LE(leading)
        // italic
        out.writeU8(0)
        // underline
        out.writeU8(0)
        // strikeOut
        out.writeU8(0)
        // weight: 16-bit LE
        out.writeU16LE(400)
        // charSet
        out.writeU8(charSet)
        // pixWidth: 16-bit LE (0 for proportional, avgWidth for mono)
        out.writeU16LE(if (isMono) avgWidth else 0)
        // pixHeight: 16-bit LE
        out.writeU16LE(height)
        // pitchAndFamily
        out.writeU8(if (isMono) 0 else 1)
        // avgWidth: 16-bit LE
        out.writeU16LE(avgWidth)
        // maxWidth: 16-bit LE
        out.writeU16LE(maxWidth)
        // firstChar
        out.writeU8(firstChar)
        // lastChar
        out.writeU8(lastChar)
        // defaultChar (relative to firstChar)
        out.writeU8(defaultChar - firstChar)
        // breakChar (relative to firstChar)
        out.writeU8(breakChar - firstChar)
        // widthBytes: 16-bit LE
        out.writeU16LE(widthBytes)
        // device: 32-bit LE
        out.writeIntLE(0)
        // face: 32-bit LE
        out.writeIntLE(face)
        // bitsPointer: 32-bit LE
        out.writeIntLE(0)
        // bitsOffset: 32-bit LE
        out.writeIntLE(bitsOffset)
        // reserved: 1 byte
        out.writeU8(0)

        if (version >= 3) {
            // flags: 32-bit LE
            out.writeIntLE(if (isMono) 0x11 else 0x12)
            // aSpace: 16-bit LE
            out.writeU16LE(0)
            // bSpace: 16-bit LE
            out.writeU16LE(0)
            // cSpace: 16-bit LE
            out.writeU16LE(0)
            // colorPointer: 32-bit LE
            out.writeIntLE(0)
            // reserved1: 16 bytes
            out.writeZeros(16)
        }

        // Character table
        var currentBitsOffset = bitsOffset
        for (idx in firstChar..lastChar + 1) {
            out.writeU16LE(widths[idx]!!)
            if (version >= 3) {
                out.writeIntLE(currentBitsOffset)
            } else {
                out.writeU16LE(currentBitsOffset)
            }
            currentBitsOffset += bitmaps[idx]!!.size
        }

        // Bitmap data
        for (idx in firstChar..lastChar + 1) {
            out.writeBytes(bitmaps[idx]!!)
        }

        // Face name (null-terminated CP1252)
        out.writeBytes(faceBytes)
        out.writeU8(0)

        return out.toByteArray()
    }

    // ---- CP1252 helpers -------------------------------------------------------

    private val CP1252_C1 = intArrayOf(
        0x20AC, 0x25CA, 0x201A, 0x0192, 0x201E, 0x2026, 0x2020, 0x2021,
        0x02C6, 0x2030, 0x0160, 0x2039, 0x0152, 0x0141, 0x017D, 0x0131,
        0x2318, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2013, 0x2014,
        0x02DC, 0x2122, 0x0161, 0x203A, 0x0153, 0x0142, 0x017E, 0x0178,
    )

    private fun fromCP1252(ch: Int): Int {
        if (ch < 0x80 || ch >= 0xA0) return ch
        return CP1252_C1[ch - 0x80]
    }

    /**
     * Encode a string to CP1252 bytes. Characters outside CP1252 are
     * replaced with '?'.
     */
    private fun encodeCp1252(s: String): ByteArray {
        val result = ByteArray(s.length)
        for (i in s.indices) {
            val c = s[i].code
            result[i] = when {
                c < 0x80 -> c.toByte()
                c in 0xA0..0xFF -> c.toByte()
                else -> {
                    // Search for the character in the C1 mapping
                    val idx = CP1252_C1.indexOf(c)
                    if (idx >= 0) (idx + 0x80).toByte() else '?'.code.toByte()
                }
            }
        }
        return result
    }
}
