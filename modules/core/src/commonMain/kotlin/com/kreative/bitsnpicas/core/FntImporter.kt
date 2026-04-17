package com.kreative.bitsnpicas.core

/**
 * Pure-Kotlin multiplatform Windows FNT bitmap font parser.
 *
 * Ported from the frozen Java `FNTBitmapFontImporter.importFontImpl`.
 * Carries the C2 (bounds-check `dx` against `data.length`) fix.
 *
 * The parser works on a raw `ByteArray` — no `java.*` dependency.
 */
public object FntImporter {

    /**
     * Exception thrown when FNT data is malformed.
     */
    public class FntParseException(message: String) : ParseException(message)

    /**
     * Parse a Windows FNT font from a raw byte array.
     *
     * @throws FntParseException on malformed / out-of-range data.
     */
    public fun read(input: ByteArray): BitmapFont = wrapParseException {
        val r = ByteReader(input)

        // The Java code reads 2 bytes big-endian as "magic" (version).
        // FNT version is stored as a little-endian WORD, but since valid
        // values are 1-3, the high byte is 0 and readU16BE works fine
        // (big-endian read of 0x01 0x00 = 0x0100, but the Java code
        // actually reads as unsigned short which is big-endian from
        // DataInputStream, and the on-disk bytes for version=2 are
        // 0x00 0x02 in LE = readUnsignedShort yields 0x0002 = 2).
        // Matching Java: readUnsignedShort reads 2 bytes big-endian.
        val magic = r.readU16BE()
        if (magic < 1 || magic > 3) throw FntParseException("bad magic number: $magic")

        // File size: 4-byte little-endian int.
        // Java: Integer.reverseBytes(in.readInt()) which reads BE then reverses = LE.
        val size = r.readIntLE()
        if (size < 118) throw FntParseException("bad size: $size")

        // Build full data buffer (Java reads remaining bytes into data[6..size-1])
        val data = ByteArray(size)
        // Copy the first 6 bytes we already consumed (magic 2 bytes + size 4 bytes)
        // into data[0..5] for positional access later.
        data[0] = ((magic shr 8) and 0xFF).toByte()
        data[1] = (magic and 0xFF).toByte()
        data[2] = (size and 0xFF).toByte()
        data[3] = ((size shr 8) and 0xFF).toByte()
        data[4] = ((size shr 16) and 0xFF).toByte()
        data[5] = ((size shr 24) and 0xFF).toByte()

        // Read the rest from input into data[6..]
        val remaining = size - 6
        if (r.remaining < remaining) {
            throw FntParseException("truncated FNT: need $remaining more bytes, have ${r.remaining}")
        }
        val restBytes = r.readBytes(remaining)
        restBytes.copyInto(data, 6)

        // Now parse from data[6..] using a new reader
        val dr = ByteReader(data)
        dr.skip(6) // skip the 6 bytes we already handled

        // Copyright: 60-byte null-terminated CP1252 string
        val copyrightBytes = dr.readBytes(60)
        val copyrightLength = copyrightBytes.indexOfFirst { it.toInt() == 0 }.let {
            if (it < 0) 60 else it
        }
        val copyright = decodeCp1252(copyrightBytes, 0, copyrightLength)

        // type: 16-bit LE unsigned
        val type = dr.readU16LE()
        if ((type and 1) != 0) throw FntParseException("vector fonts are not supported")

        val points = dr.readU16LE()
        dr.skip(2) // vertRes
        dr.skip(2) // horizRes
        val ascent = dr.readU16LE()
        dr.skip(2) // internalLeading
        val externalLeading = dr.readU16LE()
        val italic = dr.readU8()
        val underline = dr.readU8()
        val strikeOut = dr.readU8()
        val weight = dr.readU16LE()
        dr.skip(1) // charSet
        val pixWidth = dr.readU16LE()
        val pixHeight = dr.readU16LE()
        dr.skip(1) // pitchAndFamily
        dr.skip(2) // avgWidth
        dr.skip(2) // maxWidth
        val firstChar = dr.readU8()
        val lastChar = dr.readU8()
        dr.skip(1) // defaultChar
        dr.skip(1) // breakChar
        dr.skip(2) // widthBytes
        dr.skip(4) // device
        val face = dr.readIntLE()
        dr.skip(4) // bitsPointer
        dr.skip(4) // bitsOffset
        dr.skip(1) // reserved

        val flags: Int
        if (magic >= 3) {
            flags = dr.readIntLE()
            dr.skip(2) // aSpace
            dr.skip(2) // bSpace
            dr.skip(2) // cSpace
            dr.skip(4) // colorPointer
            dr.skip(16) // reserved1
        } else {
            flags = 0
        }

        // Face name: null-terminated CP1252 string starting at data[face]
        val faceName = if (face in 0 until size) {
            var faceEnd = face
            while (faceEnd < size && data[faceEnd].toInt() != 0) faceEnd++
            decodeCp1252(data, face, faceEnd - face)
        } else {
            ""
        }

        // Glyph entry tables
        val n = lastChar - firstChar + 2
        val geWidth = IntArray(n)
        val geOffset = IntArray(n)
        val geHeight = IntArray(n)

        if (magic < 3) {
            for (i in 0 until n) {
                geWidth[i] = dr.readU16LE()
                geOffset[i] = dr.readU16LE()
                geHeight[i] = pixHeight
            }
        } else {
            for (i in 0 until n) {
                geWidth[i] = dr.readU16LE()
                geOffset[i] = dr.readIntLE()
                geHeight[i] = if ((flags and 0xF0) < 0x20) {
                    pixHeight
                } else {
                    dr.readU16LE()
                }
                if ((flags and 0x0F) >= 0x04) {
                    dr.skip(12) // geAspace(4) + geBspace(4) + geCspace(4)
                }
            }
        }

        // Build font metrics
        val descent = pixHeight - ascent
        val emAscent = if (pixHeight > 0) points * ascent / pixHeight else points
        val emDescent = points - emAscent
        val styleName = styleName(italic, underline, strikeOut, weight)

        val characters = mutableMapOf<Int, BitmapGlyph>()

        for (i in 0 until n - 1) {
            val gw = geWidth[i]
            val gh = geHeight[i]
            val bitmap = List(gh) { IntArray(gw) }

            var dy = geOffset[i]
            for (by_ in 0 until gh) {
                var dx = dy
                var bx = 0
                while (bx < gw) {
                    // C2: guard against header-derived dx overrunning data
                    if (dx < 0 || dx >= data.size) {
                        throw FntParseException(
                            "FNT glyph offset $dx out of bounds (data.length=${data.size}) for glyph $i"
                        )
                    }
                    var m = 0x80
                    while (bx < gw && m != 0) {
                        if ((data[dx].toInt() and m) != 0) {
                            bitmap[by_][bx] = 0xFF
                        }
                        bx++
                        m = m shr 1
                    }
                    dx += gh
                }
                dy++
            }

            val g = BitmapGlyph(
                bitmap = bitmap,
                x = 0,
                advance = gw,
                y = ascent,
            )
            val ch = fromCP1252(firstChar + i)
            val cp = if (ch < 0) 0xF000 + firstChar + i else ch
            characters[cp] = g
        }

        // Post-processing: setXHeight / setCapHeight
        // Contract all glyphs
        for ((cp, g) in characters.toMap()) {
            characters[cp] = g.contract()
        }

        BitmapFont(
            glyphs = characters,
            emAscent = emAscent,
            emDescent = emDescent,
            lineAscent = ascent,
            lineDescent = descent,
            xHeight = guessXHeight(characters),
            capHeight = guessCapHeight(characters),
            lineGap = externalLeading,
            newGlyphWidth = pixWidth,
            name = faceName,
        )
    }

    // ---- CP1252 decoding ---------------------------------------------------

    private fun decodeCp1252(bytes: ByteArray, offset: Int, length: Int): String {
        val sb = StringBuilder(length)
        for (i in offset until offset + length) {
            val b = bytes[i].toInt() and 0xFF
            sb.append(cp1252ToChar(b))
        }
        return sb.toString()
    }

    private fun cp1252ToChar(b: Int): Char {
        // CP1252 is identical to Unicode for 0x00-0x7F and 0xA0-0xFF,
        // except for the 0x80-0x9F range which maps to specific Unicode chars.
        if (b < 0x80 || b >= 0xA0) return b.toChar()
        return CP1252_C1[b - 0x80].toChar()
    }

    // ---- Style name --------------------------------------------------------

    private fun styleName(italic: Int, underline: Int, strikeOut: Int, weight: Int): String {
        val sb = StringBuilder()
        if (weight < 400) sb.append(if (weight < 200) " Thin" else " Light")
        if (weight >= 600) sb.append(if (weight >= 900) " Black" else " Bold")
        if (italic != 0) sb.append(" Italic")
        if (underline != 0) sb.append(" Underline")
        if (strikeOut != 0) sb.append(" Strikeout")
        return if (sb.isNotEmpty()) sb.toString().trim() else "Normal"
    }

    // ---- CP1252 mapping for codepoint lookup --------------------------------

    private fun fromCP1252(ch: Int): Int {
        if (ch < 0x80 || ch >= 0xA0) return ch
        return CP1252_C1[ch - 0x80]
    }

    private val CP1252_C1 = intArrayOf(
        0x20AC, 0x25CA, 0x201A, 0x0192, 0x201E, 0x2026, 0x2020, 0x2021,
        0x02C6, 0x2030, 0x0160, 0x2039, 0x0152, 0x0141, 0x017D, 0x0131,
        0x2318, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2013, 0x2014,
        0x02DC, 0x2122, 0x0161, 0x203A, 0x0153, 0x0142, 0x017E, 0x0178,
    )

    // ---- Post-processing helpers (matching PsfImporter pattern) ----

    // Characters for cap height detection
    private val CAP_HEIGHT_CHARS = "HXTZAMNUVWYEFIJKLBDPRCGOQS5714023689\u00DE\u00C6\u00D0\u00D8bdhkl\u00FEft\u00A5!?&\u00A3\u00DF%@"
    // Characters for x-height detection
    private val X_HEIGHT_CHARS = "xzuvwymnracegopqs\u00B5\u00E6\u00F8"

    private fun guessCapHeight(chars: Map<Int, BitmapGlyph>): Int {
        for (ch in CAP_HEIGHT_CHARS) {
            val g = chars[ch.code] ?: continue
            if (g.bitmap.isEmpty()) continue
            return g.y
        }
        return 0
    }

    private fun guessXHeight(chars: Map<Int, BitmapGlyph>): Int {
        for (ch in X_HEIGHT_CHARS) {
            val g = chars[ch.code] ?: continue
            if (g.bitmap.isEmpty()) continue
            return g.y
        }
        return 0
    }

    /** Convert [ParseException] from shared ByteReader into [FntParseException]. */
    private inline fun <T> wrapParseException(block: () -> T): T {
        try {
            return block()
        } catch (e: FntParseException) {
            throw e
        } catch (e: ParseException) {
            throw FntParseException(e.message ?: "parse error")
        }
    }
}
