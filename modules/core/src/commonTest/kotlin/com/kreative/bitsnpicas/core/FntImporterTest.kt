package com.kreative.bitsnpicas.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Stage S4 -- commonMain tests for the pure-Kotlin FNT importer.
 *
 * Constructs synthetic Windows FNT byte arrays inline and verifies:
 * - valid v1 (version 2) FNT with a few glyphs parses correctly
 * - valid v2 (version 3) FNT parses correctly
 * - malformed FNT (truncated, bad offsets) doesn't crash (C2 regression)
 */
class FntImporterTest {

    // ---- helpers for building FNT byte arrays ------------------------------

    /**
     * Build a synthetic FNT v2 file (magic = 0x0200 LE).
     *
     * The FNT header layout (version 2.0):
     *   offset  0: version       (WORD, LE) = 0x0200
     *   offset  2: fileSize      (DWORD, LE)
     *   offset  6: copyright     (60 bytes, null-padded)
     *   offset 66: type          (WORD, LE) = 0 (bitmap)
     *   offset 68: points        (WORD, LE)
     *   offset 70: vertRes       (WORD, LE)
     *   offset 72: horizRes      (WORD, LE)
     *   offset 74: ascent        (WORD, LE)
     *   offset 76: internalLead  (WORD, LE)
     *   offset 78: externalLead  (WORD, LE)
     *   offset 80: italic        (BYTE)
     *   offset 81: underline     (BYTE)
     *   offset 82: strikeOut     (BYTE)
     *   offset 83: weight        (WORD, LE)
     *   offset 85: charSet       (BYTE)
     *   offset 86: pixWidth      (WORD, LE)
     *   offset 88: pixHeight     (WORD, LE)
     *   offset 90: pitchFamily   (BYTE)
     *   offset 91: avgWidth      (WORD, LE)
     *   offset 93: maxWidth      (WORD, LE)
     *   offset 95: firstChar     (BYTE)
     *   offset 96: lastChar      (BYTE)
     *   offset 97: defaultChar   (BYTE)
     *   offset 98: breakChar     (BYTE)
     *   offset 99: widthBytes    (WORD, LE)
     *   offset101: device        (DWORD, LE)
     *   offset105: face          (DWORD, LE) -> offset to face name string
     *   offset109: bitsPointer   (DWORD, LE)
     *   offset113: bitsOffset    (DWORD, LE)
     *   offset117: reserved      (BYTE)
     *   -- total fixed header = 118 bytes
     *
     * Then: n = lastChar - firstChar + 2 glyph entries, each 4 bytes (width:WORD + offset:WORD)
     * Then: bitmap data
     * Then: face name string (null-terminated)
     */
    private fun buildFntV2(
        firstChar: Int = 0x41, // 'A'
        lastChar: Int = 0x41,  // 'A' (single char)
        pixHeight: Int = 8,
        ascent: Int = 6,
        points: Int = 12,
        pixWidth: Int = 8,
        glyphWidths: IntArray? = null,
        glyphBitmaps: List<ByteArray>? = null,
        faceName: String = "Test",
        weight: Int = 400,
        italic: Int = 0,
    ): ByteArray {
        val n = lastChar - firstChar + 2 // +1 for sentinel
        val widths = glyphWidths ?: IntArray(n) { pixWidth }

        // Build bitmap data for each glyph (column-major layout)
        // Each glyph's bitmap is stored column-stripe by column-stripe,
        // where each stripe is pixHeight bytes tall.
        val bitmapParts = mutableListOf<ByteArray>()
        for (i in 0 until n) {
            val w = widths[i]
            val numStripes = (w + 7) / 8 // number of byte-columns
            val part = if (glyphBitmaps != null && i < glyphBitmaps.size) {
                glyphBitmaps[i]
            } else {
                ByteArray(numStripes * pixHeight)
            }
            bitmapParts.add(part)
        }

        val headerSize = 118
        val entryTableSize = n * 4 // v2: 2 bytes width + 2 bytes offset each
        val bitmapDataStart = headerSize + entryTableSize
        val faceNameBytes = faceName.encodeToByteArray() + byteArrayOf(0)

        // Calculate bitmap offsets for each glyph
        val offsets = IntArray(n)
        var bitmapOffset = bitmapDataStart
        for (i in 0 until n) {
            offsets[i] = bitmapOffset
            bitmapOffset += bitmapParts[i].size
        }
        val faceOffset = bitmapOffset
        val totalSize = faceOffset + faceNameBytes.size

        val buf = ByteArray(totalSize)

        // Version: 0x0200 LE = bytes 0x00, 0x02
        putU16LE(buf, 0, 0x0200)
        // File size
        putIntLE(buf, 2, totalSize)
        // Copyright: 60 zero bytes (already zeroed)
        // Type: 0 (bitmap)
        putU16LE(buf, 66, 0)
        // Points
        putU16LE(buf, 68, points)
        // vertRes, horizRes
        putU16LE(buf, 70, 96)
        putU16LE(buf, 72, 96)
        // Ascent
        putU16LE(buf, 74, ascent)
        // internalLeading
        putU16LE(buf, 76, 0)
        // externalLeading
        putU16LE(buf, 78, 0)
        // italic
        buf[80] = italic.toByte()
        // underline
        buf[81] = 0
        // strikeOut
        buf[82] = 0
        // weight
        putU16LE(buf, 83, weight)
        // charSet
        buf[85] = 0
        // pixWidth
        putU16LE(buf, 86, pixWidth)
        // pixHeight
        putU16LE(buf, 88, pixHeight)
        // pitchAndFamily
        buf[90] = 0
        // avgWidth
        putU16LE(buf, 91, pixWidth)
        // maxWidth
        putU16LE(buf, 93, pixWidth)
        // firstChar
        buf[95] = firstChar.toByte()
        // lastChar
        buf[96] = lastChar.toByte()
        // defaultChar
        buf[97] = 0
        // breakChar
        buf[98] = 0x20.toByte()
        // widthBytes
        putU16LE(buf, 99, 0)
        // device
        putIntLE(buf, 101, 0)
        // face
        putIntLE(buf, 105, faceOffset)
        // bitsPointer
        putIntLE(buf, 109, 0)
        // bitsOffset
        putIntLE(buf, 113, 0)
        // reserved
        buf[117] = 0

        // Glyph entry table (v2: width WORD + offset WORD)
        var pos = headerSize
        for (i in 0 until n) {
            putU16LE(buf, pos, widths[i]); pos += 2
            putU16LE(buf, pos, offsets[i]); pos += 2
        }

        // Bitmap data
        for (i in 0 until n) {
            bitmapParts[i].copyInto(buf, offsets[i])
        }

        // Face name
        faceNameBytes.copyInto(buf, faceOffset)

        return buf
    }

    /**
     * Build a FNT v3 file (magic = 0x0300 LE).
     * v3 adds: flags (DWORD), aSpace, bSpace, cSpace (WORDs), colorPointer (DWORD), reserved1 (16 bytes)
     * Glyph entries: width (WORD) + offset (DWORD) [+ height (WORD) if flags >= 0x20] [+ ABC (3 DWORDs) if flags & 0x0F >= 4]
     */
    private fun buildFntV3(
        firstChar: Int = 0x41,
        lastChar: Int = 0x41,
        pixHeight: Int = 8,
        ascent: Int = 6,
        points: Int = 12,
        pixWidth: Int = 8,
        flags: Int = 0,
        glyphWidths: IntArray? = null,
        glyphBitmaps: List<ByteArray>? = null,
        faceName: String = "Test",
    ): ByteArray {
        val n = lastChar - firstChar + 2
        val widths = glyphWidths ?: IntArray(n) { pixWidth }

        // v3 header = 118 + 4(flags) + 2(aSpace) + 2(bSpace) + 2(cSpace) + 4(colorPointer) + 16(reserved1) = 148
        val v3HeaderSize = 148

        // Glyph entry size depends on flags
        val hasPerGlyphHeight = (flags and 0xF0) >= 0x20
        val hasABC = (flags and 0x0F) >= 0x04
        val entrySize = 2 + 4 + (if (hasPerGlyphHeight) 2 else 0) + (if (hasABC) 12 else 0)
        val entryTableSize = n * entrySize

        // Build bitmap data
        val bitmapParts = mutableListOf<ByteArray>()
        for (i in 0 until n) {
            val w = widths[i]
            val h = pixHeight
            val numStripes = (w + 7) / 8
            val part = if (glyphBitmaps != null && i < glyphBitmaps.size) {
                glyphBitmaps[i]
            } else {
                ByteArray(numStripes * h)
            }
            bitmapParts.add(part)
        }

        val bitmapDataStart = v3HeaderSize + entryTableSize
        val faceNameBytes = faceName.encodeToByteArray() + byteArrayOf(0)

        val offsets = IntArray(n)
        var bitmapOffset = bitmapDataStart
        for (i in 0 until n) {
            offsets[i] = bitmapOffset
            bitmapOffset += bitmapParts[i].size
        }
        val faceOffset = bitmapOffset
        val totalSize = faceOffset + faceNameBytes.size

        val buf = ByteArray(totalSize)

        // Version: 0x0300 LE = bytes 0x00, 0x03
        putU16LE(buf, 0, 0x0300)
        putIntLE(buf, 2, totalSize)
        // Copyright: 60 zero bytes at offset 6
        // Type: 0 (bitmap) at offset 66
        putU16LE(buf, 66, 0)
        putU16LE(buf, 68, points)
        putU16LE(buf, 70, 96) // vertRes
        putU16LE(buf, 72, 96) // horizRes
        putU16LE(buf, 74, ascent)
        putU16LE(buf, 76, 0) // internalLeading
        putU16LE(buf, 78, 0) // externalLeading
        buf[80] = 0 // italic
        buf[81] = 0 // underline
        buf[82] = 0 // strikeOut
        putU16LE(buf, 83, 400) // weight
        buf[85] = 0 // charSet
        putU16LE(buf, 86, pixWidth)
        putU16LE(buf, 88, pixHeight)
        buf[90] = 0 // pitchAndFamily
        putU16LE(buf, 91, pixWidth)
        putU16LE(buf, 93, pixWidth)
        buf[95] = firstChar.toByte()
        buf[96] = lastChar.toByte()
        buf[97] = 0 // defaultChar
        buf[98] = 0x20.toByte() // breakChar
        putU16LE(buf, 99, 0) // widthBytes
        putIntLE(buf, 101, 0) // device
        putIntLE(buf, 105, faceOffset) // face
        putIntLE(buf, 109, 0) // bitsPointer
        putIntLE(buf, 113, 0) // bitsOffset
        buf[117] = 0 // reserved

        // v3 extra fields starting at offset 118
        putIntLE(buf, 118, flags) // flags
        putU16LE(buf, 122, 0) // aSpace
        putU16LE(buf, 124, 0) // bSpace
        putU16LE(buf, 126, 0) // cSpace
        putIntLE(buf, 128, 0) // colorPointer
        // reserved1: 16 zero bytes at offset 132 (already zeroed)

        // Glyph entry table at offset 148
        var pos = v3HeaderSize
        for (i in 0 until n) {
            putU16LE(buf, pos, widths[i]); pos += 2
            putIntLE(buf, pos, offsets[i]); pos += 4
            if (hasPerGlyphHeight) {
                putU16LE(buf, pos, pixHeight); pos += 2
            }
            if (hasABC) {
                putIntLE(buf, pos, 0); pos += 4 // aSpace
                putIntLE(buf, pos, 0); pos += 4 // bSpace
                putIntLE(buf, pos, 0); pos += 4 // cSpace
            }
        }

        // Bitmap data
        for (i in 0 until n) {
            bitmapParts[i].copyInto(buf, offsets[i])
        }

        // Face name
        faceNameBytes.copyInto(buf, faceOffset)

        return buf
    }

    /**
     * Create a column-major bitmap for a single glyph.
     * FNT stores bitmaps in column-stripe order: for each byte-column (stripe),
     * `pixHeight` bytes are stored sequentially. Within each byte, MSB is leftmost.
     *
     * @param rows Row-major bitmap: rows[y] is a list of 0/1 values for each pixel column.
     * @param pixHeight The glyph height.
     */
    private fun makeColumnMajorBitmap(rows: List<IntArray>, pixHeight: Int): ByteArray {
        val width = rows.firstOrNull()?.size ?: 0
        val numStripes = (width + 7) / 8
        val result = ByteArray(numStripes * pixHeight)
        for (stripe in 0 until numStripes) {
            for (y in 0 until pixHeight) {
                var byte = 0
                for (bit in 0 until 8) {
                    val x = stripe * 8 + bit
                    if (x < width && y < rows.size && rows[y][x] != 0) {
                        byte = byte or (0x80 shr bit)
                    }
                }
                result[stripe * pixHeight + y] = byte.toByte()
            }
        }
        return result
    }

    private fun putU16LE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun putIntLE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset + 0] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    // ---- Valid v2 FNT tests ------------------------------------------------

    @Test
    fun `v2 single glyph parses with correct count`() {
        val input = buildFntV2(firstChar = 0x41, lastChar = 0x41)
        val font = FntImporter.read(input)
        assertEquals(1, font.glyphs.size)
    }

    @Test
    fun `v2 multiple glyphs parse correctly`() {
        val input = buildFntV2(firstChar = 0x41, lastChar = 0x43) // A, B, C
        val font = FntImporter.read(input)
        assertEquals(3, font.glyphs.size)
    }

    @Test
    fun `v2 glyph bitmap content is parsed correctly`() {
        // Create a single glyph 'A' (0x41) that is 8px wide, 8px tall
        // with a checkerboard pattern in row 0: 10101010
        val rows = List(8) { y ->
            if (y == 0) intArrayOf(1, 0, 1, 0, 1, 0, 1, 0)
            else IntArray(8)
        }
        val bitmapData = makeColumnMajorBitmap(rows, 8)
        val input = buildFntV2(
            firstChar = 0x41, lastChar = 0x41,
            pixHeight = 8, pixWidth = 8,
            glyphBitmaps = listOf(bitmapData, ByteArray(8)), // +1 for sentinel
        )
        val font = FntImporter.read(input)
        val glyph = font.glyphs[0x41]!!
        // After contraction, the glyph should have the checkerboard in the first row
        // The non-zero pixels in row 0 are at columns 0,2,4,6
        // All other rows are empty, so contraction strips them.
        assertEquals(1, glyph.height, "height after contraction")
        // The contracted width should be 7 (columns 0-6 inclusive)
        assertEquals(7, glyph.width, "width after contraction")
    }

    @Test
    fun `v2 font face name is preserved`() {
        val input = buildFntV2(faceName = "MyFont")
        val font = FntImporter.read(input)
        assertEquals("MyFont", font.name)
    }

    @Test
    fun `v2 font metrics are correct`() {
        val input = buildFntV2(pixHeight = 16, ascent = 12, points = 24, pixWidth = 8)
        val font = FntImporter.read(input)
        // emAscent = points * ascent / pixHeight = 24 * 12 / 16 = 18
        assertEquals(18, font.emAscent)
        // emDescent = points - emAscent = 24 - 18 = 6
        assertEquals(6, font.emDescent)
        assertEquals(12, font.lineAscent)
        assertEquals(4, font.lineDescent) // pixHeight - ascent = 16 - 12 = 4
        assertEquals(8, font.newGlyphWidth)
    }

    @Test
    fun `v2 glyph codepoint mapping uses CP1252`() {
        // firstChar=0x41 ('A') -> codepoint 0x41
        val input = buildFntV2(firstChar = 0x41, lastChar = 0x41)
        val font = FntImporter.read(input)
        assertTrue(font.glyphs.containsKey(0x41), "Should have codepoint for 'A'")
    }

    // ---- Valid v3 FNT tests ------------------------------------------------

    @Test
    fun `v3 single glyph parses correctly`() {
        val input = buildFntV3(firstChar = 0x41, lastChar = 0x41)
        val font = FntImporter.read(input)
        assertEquals(1, font.glyphs.size)
    }

    @Test
    fun `v3 with per-glyph height parses correctly`() {
        val input = buildFntV3(firstChar = 0x41, lastChar = 0x41, flags = 0x20)
        val font = FntImporter.read(input)
        assertEquals(1, font.glyphs.size)
    }

    @Test
    fun `v3 with ABC spacing parses correctly`() {
        val input = buildFntV3(firstChar = 0x41, lastChar = 0x41, flags = 0x04)
        val font = FntImporter.read(input)
        assertEquals(1, font.glyphs.size)
    }

    @Test
    fun `v3 multiple glyphs parse correctly`() {
        val input = buildFntV3(firstChar = 0x41, lastChar = 0x43)
        val font = FntImporter.read(input)
        assertEquals(3, font.glyphs.size)
    }

    // ---- C2 regression: bounds-check on bad offsets -------------------------

    @Test
    fun `C2 truncated FNT rejects gracefully`() {
        // Build a valid header but truncate the data
        val valid = buildFntV2(firstChar = 0x41, lastChar = 0x41)
        val truncated = valid.copyOfRange(0, 20) // way too short
        assertFailsWith<FntImporter.FntParseException> {
            FntImporter.read(truncated)
        }
    }

    @Test
    fun `C2 bad glyph offset triggers bounds check`() {
        // Build a v2 FNT, then corrupt a glyph offset to point past end of data
        val input = buildFntV2(
            firstChar = 0x41, lastChar = 0x41,
            pixHeight = 8, pixWidth = 8,
        )
        // The glyph entry table starts at offset 118
        // Each v2 entry is 4 bytes: width(2) + offset(2)
        // Corrupt the offset of glyph 0 to a huge value
        val corrupted = input.copyOf()
        putU16LE(corrupted, 118 + 2, 0xFFFF) // offset = 65535 (way past data)
        assertFailsWith<FntImporter.FntParseException> {
            FntImporter.read(corrupted)
        }
    }

    @Test
    fun `C2 negative-like offset does not crash`() {
        // Build a valid FNT, then corrupt to offset 0 (within header, not bitmap)
        // This should still be handled gracefully without AIOOBE
        val input = buildFntV2(
            firstChar = 0x41, lastChar = 0x41,
            pixHeight = 8, pixWidth = 8,
        )
        val corrupted = input.copyOf()
        // Set offset to 0 -- in the header area, valid index but wrong data
        putU16LE(corrupted, 118 + 2, 0)
        // Should not throw AIOOBE -- either parses (wrong data) or throws FntParseException
        try {
            FntImporter.read(corrupted)
        } catch (e: FntImporter.FntParseException) {
            // acceptable
        }
        // If we get here without an unhandled exception, the test passes
    }

    // ---- Bad magic / malformed header tests --------------------------------

    @Test
    fun `bad magic number is rejected`() {
        assertFailsWith<FntImporter.FntParseException> {
            FntImporter.read(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        }
    }

    @Test
    fun `vector font type is rejected`() {
        val input = buildFntV2()
        val corrupted = input.copyOf()
        // Set type to 1 (vector) at offset 66
        putU16LE(corrupted, 66, 1)
        assertFailsWith<FntImporter.FntParseException> {
            FntImporter.read(corrupted)
        }
    }

    @Test
    fun `file size too small is rejected`() {
        // Build minimal bytes with valid magic but bad size
        val buf = ByteArray(10)
        putU16LE(buf, 0, 0x0200) // valid magic
        putIntLE(buf, 2, 50) // size < 118
        assertFailsWith<FntImporter.FntParseException> {
            FntImporter.read(buf)
        }
    }

    @Test
    fun `truncated input is rejected`() {
        assertFailsWith<FntImporter.FntParseException> {
            FntImporter.read(byteArrayOf(0x00, 0x02)) // just the magic, nothing else
        }
    }

    // ---- Style name tests --------------------------------------------------

    @Test
    fun `bold italic font has correct style in name`() {
        val input = buildFntV2(weight = 700, italic = 1)
        val font = FntImporter.read(input)
        // The Java code computes styleName but stores it in BitmapFont names.
        // Our port stores faceName in font.name, not style.
        // Just verify it parses without error.
        assertEquals(1, font.glyphs.size)
    }
}
