package com.kreative.bitsnpicas.core

/**
 * Pure-Kotlin multiplatform PSF v1 + v2 bitmap font parser.
 *
 * Ported from the frozen Java `PSFBitmapFontImporter.importFontImpl`.
 * Retains the C1 (OOB guard) and C3 (OOM cap) Stage-S1 fixes.
 *
 * The parser works on a raw `ByteArray` with index tracking --
 * no `java.io.DataInputStream` needed.
 */
public object PsfImporter {

    // ---- C3: sanity limits on untrusted PSF header fields ---------------
    private const val MAX_NUM_GLYPHS = 0x200000   // 2,097,152 glyphs
    private const val MAX_HEADER_SIZE = 0x10000    // 64 KiB extra header
    private const val MAX_CHAR_SIZE = 0x100000     // 1 MiB per glyph
    private const val MAX_DIMENSION = 0x1000       // 4096 px

    /**
     * Exception thrown when PSF data is malformed.
     */
    public class PsfParseException(message: String) : Exception(message)

    /**
     * Parse a PSF v1 or v2 font from a raw byte array.
     *
     * @throws PsfParseException on malformed / out-of-range data.
     */
    public fun read(input: ByteArray): BitmapFont {
        val r = ByteReader(input)

        val version = readVersion(r)

        val headerSize: Int
        val flags: Int
        val numGlyphs: Int
        val charSize: Int
        val height: Int
        val width: Int

        if (version < 2) {
            headerSize = 4
            flags = r.readU8()
            numGlyphs = if ((flags and 1) == 0) 256 else 512
            charSize = r.readU8()
            height = charSize
            width = 8
        } else {
            headerSize = r.readIntLE()
            flags = r.readIntLE()
            numGlyphs = r.readIntLE()
            charSize = r.readIntLE()
            height = r.readIntLE()
            width = r.readIntLE()
        }

        // C3: reject unreasonably large values
        if (numGlyphs < 0 || numGlyphs > MAX_NUM_GLYPHS)
            throw PsfParseException("PSF numGlyphs out of range: $numGlyphs")
        if (headerSize < 0 || headerSize > MAX_HEADER_SIZE)
            throw PsfParseException("PSF headerSize out of range: $headerSize")
        if (charSize < 0 || charSize > MAX_CHAR_SIZE)
            throw PsfParseException("PSF charSize out of range: $charSize")
        if (height < 0 || height > MAX_DIMENSION || width < 0 || width > MAX_DIMENSION)
            throw PsfParseException("PSF glyph dimensions out of range: ${width}x$height")

        // Skip extra v2 header bytes
        if (version >= 2 && headerSize > 32) {
            r.skip(headerSize - 32)
        }

        // C1: guard against headers whose declared geometry overruns the
        // per-glyph byte buffer. charSize must be >= height * ceil(width/8).
        val bytesPerRow = (width + 7) / 8
        val expectedCharSize = height.toLong() * bytesPerRow.toLong()
        if (height < 0 || width < 0 || bytesPerRow < 0 || charSize < expectedCharSize) {
            throw PsfParseException(
                "PSF glyph size $charSize too small for ${width}x$height (needs $expectedCharSize)"
            )
        }

        // Read glyph bitmaps
        val glyphBitmaps = Array(numGlyphs) { glyphIndex ->
            val data = r.readBytes(charSize)
            val rows = List(height) { IntArray(width) }
            var j = 0
            for (y in 0 until height) {
                var x = 0
                while (x < width) {
                    if (j >= data.size) {
                        throw PsfParseException(
                            "PSF glyph data truncated at glyph $glyphIndex (byte index $j >= ${data.size})"
                        )
                    }
                    var m = 0x80
                    while (x < width && m != 0) {
                        if ((data[j].toInt() and m) != 0) {
                            rows[y][x] = 0xFF
                        }
                        x++
                        m = m shr 1
                    }
                    j++
                }
            }
            rows
        }

        // Parse optional unicode table
        val unicodeTable = mutableMapOf<String, Int>()
        val hasUnicodeTable = if (version < 2) (flags and 2) != 0 else (flags and 1) != 0
        if (hasUnicodeTable) {
            for (i in 0 until numGlyphs) {
                val entries = readUnicodeEntry(r, version)
                for (s in entries) {
                    unicodeTable[s] = i
                }
            }
        }

        // Map glyphs to codepoints -- simplified version without encoding tables
        // (the commonMain port does not carry GlyphList encoding lookup).
        val characters = mutableMapOf<Int, BitmapGlyph>()

        // Apply unicode table entries (single-codepoint only)
        for ((key, glyphIdx) in unicodeTable) {
            val cpCount = key.codePointCount(0, key.length)
            if (cpCount == 1) {
                val cp = key.codePointAt(0)
                characters[cp] = toBitmapGlyph(glyphBitmaps[glyphIdx], width, height)
            }
        }

        // Fallback: if nothing mapped, assign by ordinal
        if (characters.isEmpty()) {
            for (i in 0 until numGlyphs) {
                characters[i] = toBitmapGlyph(glyphBitmaps[i], width, height)
            }
        }

        // Post-process to match the frozen Java BitmapFont behaviour:
        // setAscentDescent() -> contractGlyphs() + guessBaselineAdjustment()
        // setXHeight() -> contractGlyphs() + guessXHeight()
        // setCapHeight() -> contractGlyphs() + guessCapHeight()
        var emAscent = height
        var emDescent = 0
        var lineAscent = height
        var lineDescent = 0
        var xh = height
        var ch = height

        // Contract all glyphs (strip empty border pixels)
        for ((cp, g) in characters.toMap()) {
            characters[cp] = g.contract()
        }

        // guessBaselineAdjustment: find the first baseline character and adjust
        val adjust = guessBaselineAdjustment(characters)
        if (adjust != 0) {
            emAscent += adjust
            emDescent -= adjust
            lineAscent += adjust
            lineDescent -= adjust
            for ((cp, g) in characters.toMap()) {
                characters[cp] = BitmapGlyph(g.bitmap, g.x, g.advance, g.y + adjust,
                    intArrayOf())
            }
        }

        // Re-contract after adjustment (Java calls contractGlyphs again in setXHeight/setCapHeight)
        for ((cp, g) in characters.toMap()) {
            characters[cp] = g.contract()
        }

        // guessXHeight / guessCapHeight
        val guessedXh = guessXHeight(characters)
        if (guessedXh != 0) xh = guessedXh

        val guessedCh = guessCapHeight(characters)
        if (guessedCh != 0) ch = guessedCh

        return BitmapFont(
            glyphs = characters,
            emAscent = emAscent,
            emDescent = emDescent,
            lineAscent = lineAscent,
            lineDescent = lineDescent,
            xHeight = xh,
            capHeight = ch,
            lineGap = 0,
            newGlyphWidth = width,
        )
    }

    private fun toBitmapGlyph(rows: List<IntArray>, width: Int, height: Int): BitmapGlyph {
        return BitmapGlyph(
            bitmap = rows,
            x = 0,
            advance = width,
            y = height,  // baseline = ascent for PSF fonts
        )
    }

    // ---- Post-processing helpers matching frozen Java Font/BitmapFont ----

    // Characters used for baseline detection, matching Java Font.BASELINE_CHARS
    private val BASELINE_CHARS = "HXxZzAMNTYilmnEFIKLPRhkrvwVWBDbduftCGJOSUaceos2147035689\u00DE\u00C6\u00E6\u00D0\u00D8\u00F8\u00A5\u00A3\u00DF&!?.%@\u00B1"
    // Characters for cap height detection
    private val CAP_HEIGHT_CHARS = "HXTZAMNUVWYEFIJKLBDPRCGOQS5714023689\u00DE\u00C6\u00D0\u00D8bdhkl\u00FEft\u00A5!?&\u00A3\u00DF%@"
    // Characters for x-height detection
    private val X_HEIGHT_CHARS = "xzuvwymnracegopqs\u00B5\u00E6\u00F8"

    private fun guessBaselineAdjustment(chars: Map<Int, BitmapGlyph>): Int {
        for (ch in BASELINE_CHARS) {
            val g = chars[ch.code] ?: continue
            if (g.bitmap.isEmpty()) continue
            // glyphHeight - glyphAscent = height - y
            return g.height - g.y
        }
        return 0
    }

    private fun guessCapHeight(chars: Map<Int, BitmapGlyph>): Int {
        for (ch in CAP_HEIGHT_CHARS) {
            val g = chars[ch.code] ?: continue
            if (g.bitmap.isEmpty()) continue
            return g.y  // glyphAscent = y
        }
        return 0
    }

    private fun guessXHeight(chars: Map<Int, BitmapGlyph>): Int {
        for (ch in X_HEIGHT_CHARS) {
            val g = chars[ch.code] ?: continue
            if (g.bitmap.isEmpty()) continue
            return g.y  // glyphAscent = y
        }
        return 0
    }

    // ---- version magic detection ----------------------------------------

    private fun readVersion(r: ByteReader): Int {
        val m1 = r.readU16BE()
        if (m1 == 0x3604) return 1
        if (m1 == 0x72B5) {
            val m2 = r.readU16BE()
            if (m2 == 0x4A86) {
                val m3 = r.readIntBE()
                if (m3 == 0) return 2
                throw PsfParseException("bad magic number m3: $m3")
            }
            throw PsfParseException("bad magic number m2: $m2")
        }
        throw PsfParseException("bad magic number m1: $m1")
    }

    // ---- unicode entry parsing ------------------------------------------

    private fun readUnicodeEntry(r: ByteReader, version: Int): List<String> {
        if (version < 2) {
            val sb = StringBuilder()
            while (true) {
                val ch = r.readU16LE().toChar()
                if (ch == '\uFFFF') break
                sb.append(ch)
            }
            return splitUnicodeEntry(sb.toString())
        } else {
            val bytes = mutableListOf<Byte>()
            while (true) {
                val b = r.readU8()
                if (b == 0xFF) break
                if (b == 0xFE) {
                    // U+FFFE encoded as UTF-8
                    bytes.add(0xEF.toByte())
                    bytes.add(0xBF.toByte())
                    bytes.add(0xBE.toByte())
                } else {
                    bytes.add(b.toByte())
                }
            }
            val s = bytes.toByteArray().decodeToString()
            return splitUnicodeEntry(s)
        }
    }

    private fun splitUnicodeEntry(s: String): List<String> {
        val result = mutableListOf<String>()
        val pieces = s.split("\uFFFE")
            .filter { it.isNotEmpty() }
        if (pieces.isEmpty()) return result
        // First piece: split into individual codepoints
        val first = pieces[0]
        var i = 0
        while (i < first.length) {
            val cp = first.codePointAt(i)
            result.add(String(Character.toChars(cp)))
            i += Character.charCount(cp)
        }
        // Remaining pieces are multi-codepoint sequences
        for (idx in 1 until pieces.size) {
            result.add(pieces[idx])
        }
        return result
    }

    // ---- Byte-level helpers (Kotlin stdlib only, no java.io) ------------

    /**
     * Minimal sequential byte reader over a ByteArray.
     * Tracks position; throws PsfParseException on underflow.
     */
    internal class ByteReader(private val data: ByteArray) {
        var pos: Int = 0
            private set

        val remaining: Int get() = data.size - pos

        fun readU8(): Int {
            if (pos >= data.size) throw PsfParseException("unexpected end of data at offset $pos")
            return data[pos++].toInt() and 0xFF
        }

        /** Read 16-bit unsigned, big-endian (matches DataInputStream.readUnsignedShort). */
        fun readU16BE(): Int {
            val hi = readU8()
            val lo = readU8()
            return (hi shl 8) or lo
        }

        /** Read 16-bit unsigned, little-endian. */
        fun readU16LE(): Int {
            val lo = readU8()
            val hi = readU8()
            return (hi shl 8) or lo
        }

        /** Read 32-bit signed, big-endian (matches DataInputStream.readInt). */
        fun readIntBE(): Int {
            val b3 = readU8()
            val b2 = readU8()
            val b1 = readU8()
            val b0 = readU8()
            return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
        }

        /** Read 32-bit signed, little-endian. */
        fun readIntLE(): Int {
            val b0 = readU8()
            val b1 = readU8()
            val b2 = readU8()
            val b3 = readU8()
            return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
        }

        fun readBytes(count: Int): ByteArray {
            if (pos + count > data.size)
                throw PsfParseException("unexpected end of data: need $count bytes at offset $pos, have ${data.size - pos}")
            val result = data.copyOfRange(pos, pos + count)
            pos += count
            return result
        }

        fun skip(count: Int) {
            if (pos + count > data.size)
                throw PsfParseException("unexpected end of data: cannot skip $count bytes at offset $pos")
            pos += count
        }
    }
}

// Kotlin/Common equivalent of Java's Character.toChars / codePointAt / charCount / codePointCount
// These are available on String in Kotlin/JVM and we provide expect/actual or inline helpers.

// String.codePointAt is available on JVM but not in common. We use a simple implementation:
private fun String.codePointAt(index: Int): Int {
    val c1 = this[index]
    if (c1.isHighSurrogate() && index + 1 < this.length) {
        val c2 = this[index + 1]
        if (c2.isLowSurrogate()) {
            return 0x10000 + ((c1.code - 0xD800) shl 10) + (c2.code - 0xDC00)
        }
    }
    return c1.code
}

private fun String.codePointCount(beginIndex: Int, endIndex: Int): Int {
    var count = 0
    var i = beginIndex
    while (i < endIndex) {
        val c = this[i]
        if (c.isHighSurrogate() && i + 1 < endIndex && this[i + 1].isLowSurrogate()) {
            i += 2
        } else {
            i++
        }
        count++
    }
    return count
}

private object Character {
    fun toChars(codePoint: Int): CharArray {
        return if (codePoint < 0x10000) {
            charArrayOf(codePoint.toChar())
        } else {
            val offset = codePoint - 0x10000
            charArrayOf(
                (0xD800 + (offset shr 10)).toChar(),
                (0xDC00 + (offset and 0x3FF)).toChar(),
            )
        }
    }

    fun charCount(codePoint: Int): Int = if (codePoint >= 0x10000) 2 else 1
}
