package com.kreative.bitsnpicas.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Stage S4 differential-parity gate for the FNT importer.
 *
 * Each valid FNT fixture is imported via both the commonMain
 * `FntImporter.read` and the frozen Java `JavaLegacyAdapter.importFntViaJava`.
 * The resulting `BitmapFont` must agree on glyph count, dimensions,
 * and bitmap bytes.
 */
class FntImporterJvmParityTest {

    // ---- helpers for building FNT byte arrays ------------------------------

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

    /**
     * Build a synthetic FNT v2 file (magic = 0x0200 LE).
     */
    private fun buildFntV2(
        firstChar: Int = 0x41,
        lastChar: Int = 0x41,
        pixHeight: Int = 8,
        ascent: Int = 6,
        points: Int = 12,
        pixWidth: Int = 8,
        glyphWidths: IntArray? = null,
        glyphBitmaps: List<ByteArray>? = null,
        faceName: String = "Test",
    ): ByteArray {
        val n = lastChar - firstChar + 2
        val widths = glyphWidths ?: IntArray(n) { pixWidth }

        val bitmapParts = mutableListOf<ByteArray>()
        for (i in 0 until n) {
            val w = widths[i]
            val numStripes = (w + 7) / 8
            val part = if (glyphBitmaps != null && i < glyphBitmaps.size) {
                glyphBitmaps[i]
            } else {
                ByteArray(numStripes * pixHeight)
            }
            bitmapParts.add(part)
        }

        val headerSize = 118
        val entryTableSize = n * 4
        val bitmapDataStart = headerSize + entryTableSize
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

        putU16LE(buf, 0, 0x0200)
        putIntLE(buf, 2, totalSize)
        putU16LE(buf, 66, 0) // type
        putU16LE(buf, 68, points)
        putU16LE(buf, 70, 96)
        putU16LE(buf, 72, 96)
        putU16LE(buf, 74, ascent)
        putU16LE(buf, 76, 0)
        putU16LE(buf, 78, 0)
        buf[80] = 0
        buf[81] = 0
        buf[82] = 0
        putU16LE(buf, 83, 400)
        buf[85] = 0
        putU16LE(buf, 86, pixWidth)
        putU16LE(buf, 88, pixHeight)
        buf[90] = 0
        putU16LE(buf, 91, pixWidth)
        putU16LE(buf, 93, pixWidth)
        buf[95] = firstChar.toByte()
        buf[96] = lastChar.toByte()
        buf[97] = 0
        buf[98] = 0x20.toByte()
        putU16LE(buf, 99, 0)
        putIntLE(buf, 101, 0)
        putIntLE(buf, 105, faceOffset)
        putIntLE(buf, 109, 0)
        putIntLE(buf, 113, 0)
        buf[117] = 0

        var pos = headerSize
        for (i in 0 until n) {
            putU16LE(buf, pos, widths[i]); pos += 2
            putU16LE(buf, pos, offsets[i]); pos += 2
        }

        for (i in 0 until n) {
            bitmapParts[i].copyInto(buf, offsets[i])
        }

        faceNameBytes.copyInto(buf, faceOffset)

        return buf
    }

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

    // ---- parity assertion --------------------------------------------------

    private fun assertParity(input: ByteArray) {
        val kotlin = FntImporter.read(input)
        val java = JavaLegacyAdapter.importFntViaJava(input)

        assertEquals(java.glyphs.size, kotlin.glyphs.size, "glyph count mismatch")
        assertEquals(java.emAscent, kotlin.emAscent, "emAscent mismatch")
        assertEquals(java.emDescent, kotlin.emDescent, "emDescent mismatch")
        assertEquals(java.lineAscent, kotlin.lineAscent, "lineAscent mismatch")
        assertEquals(java.lineDescent, kotlin.lineDescent, "lineDescent mismatch")
        assertEquals(java.newGlyphWidth, kotlin.newGlyphWidth, "newGlyphWidth mismatch")

        for ((cp, jGlyph) in java.glyphs) {
            val kGlyph = kotlin.glyphs[cp]
                ?: error("Kotlin result missing codepoint $cp (0x${cp.toString(16)})")
            assertEquals(jGlyph.width, kGlyph.width, "width mismatch at cp=$cp")
            assertEquals(jGlyph.height, kGlyph.height, "height mismatch at cp=$cp")
            assertEquals(jGlyph.advance, kGlyph.advance, "advance mismatch at cp=$cp")
            assertEquals(jGlyph.x, kGlyph.x, "x mismatch at cp=$cp")
            assertEquals(jGlyph.y, kGlyph.y, "y mismatch at cp=$cp")
            for (row in 0 until jGlyph.height) {
                assertEquals(
                    jGlyph.bitmap[row].toList(),
                    kGlyph.bitmap[row].toList(),
                    "bitmap row $row mismatch at cp=$cp",
                )
            }
        }
    }

    // ---- v2 parity tests ---------------------------------------------------

    @Test
    fun `v2 single glyph all-zero parity`() {
        assertParity(buildFntV2(firstChar = 0x41, lastChar = 0x41))
    }

    @Test
    fun `v2 multiple glyphs all-zero parity`() {
        assertParity(buildFntV2(firstChar = 0x41, lastChar = 0x43))
    }

    @Test
    fun `v2 with bitmap content parity`() {
        val rows = List(8) { y ->
            if (y == 0) intArrayOf(1, 1, 1, 1, 0, 0, 0, 0)
            else if (y == 1) intArrayOf(0, 0, 0, 0, 1, 1, 1, 1)
            else IntArray(8)
        }
        val bitmap = makeColumnMajorBitmap(rows, 8)
        val sentinel = ByteArray(8) // sentinel glyph
        assertParity(buildFntV2(
            firstChar = 0x41, lastChar = 0x41,
            pixHeight = 8, pixWidth = 8,
            glyphBitmaps = listOf(bitmap, sentinel),
        ))
    }

    @Test
    fun `v2 wide glyph parity`() {
        // 16px wide glyph = 2 stripes
        val widths = intArrayOf(16, 16) // glyph + sentinel
        val rows = List(4) { IntArray(16) { 1 } } + List(4) { IntArray(16) }
        val bitmap = makeColumnMajorBitmap(rows, 8)
        val sentinel = ByteArray(2 * 8)
        assertParity(buildFntV2(
            firstChar = 0x41, lastChar = 0x41,
            pixHeight = 8, pixWidth = 16,
            glyphWidths = widths,
            glyphBitmaps = listOf(bitmap, sentinel),
        ))
    }

    @Test
    fun `v2 range of ASCII chars parity`() {
        // A-Z range
        assertParity(buildFntV2(firstChar = 0x41, lastChar = 0x5A))
    }

    @Test
    fun `v2 font metrics parity`() {
        assertParity(buildFntV2(
            firstChar = 0x41, lastChar = 0x41,
            pixHeight = 16, ascent = 12, points = 24, pixWidth = 10,
        ))
    }
}
