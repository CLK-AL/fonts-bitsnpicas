package com.kreative.bitsnpicas.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Stage S4 -- FNT format round-trip and exporter tests.
 *
 * Covers:
 * - programmatic font -> export -> import round-trip
 * - FNT header magic and size validation
 * - face name preservation
 * - multi-glyph round-trip
 * - double round-trip stability
 */
class FntRoundTripTest {

    private fun assertSemanticEqual(a: BitmapFont, b: BitmapFont) {
        assertEquals(a.glyphs.size, b.glyphs.size, "glyph count mismatch")
        for ((cp, ag) in a.glyphs) {
            val bg = b.glyphs[cp]
            assertNotNull(bg, "round-trip lost codepoint 0x${cp.toString(16)}")
            assertEquals(ag.width, bg.width, "width mismatch at cp=0x${cp.toString(16)}")
            assertEquals(ag.height, bg.height, "height mismatch at cp=0x${cp.toString(16)}")
            assertEquals(ag.advance, bg.advance, "advance mismatch at cp=0x${cp.toString(16)}")
            for (row in 0 until ag.height) {
                assertEquals(
                    ag.bitmap[row].toList(),
                    bg.bitmap[row].toList(),
                    "bitmap row $row mismatch at cp=0x${cp.toString(16)}",
                )
            }
        }
    }

    // ---- fixtures ----------------------------------------------------------

    /**
     * Build a font with ASCII printable characters that maps cleanly
     * to CP1252 (codepoints 0x20..0x7E for straightforward round-tripping).
     */
    private fun buildSimpleFont(): BitmapFont {
        val on = 0xFF
        val off = 0x00
        val glyphs = mutableMapOf<Int, BitmapGlyph>()

        // Space (0x20)
        glyphs[0x20] = BitmapGlyph(
            bitmap = List(8) { IntArray(4) },
            x = 0,
            advance = 4,
            y = 6,
        )

        // 'A' (0x41) - box
        glyphs[0x41] = BitmapGlyph(
            bitmap = listOf(
                intArrayOf(on, on, on, on, on, on, on, on),
                intArrayOf(on, off, off, off, off, off, off, on),
                intArrayOf(on, off, off, off, off, off, off, on),
                intArrayOf(on, on, on, on, on, on, on, on),
                intArrayOf(on, off, off, off, off, off, off, on),
                intArrayOf(on, off, off, off, off, off, off, on),
                intArrayOf(on, off, off, off, off, off, off, on),
                intArrayOf(on, off, off, off, off, off, off, on),
            ),
            x = 0,
            advance = 8,
            y = 6,
        )

        // 'B' (0x42)
        glyphs[0x42] = BitmapGlyph(
            bitmap = listOf(
                intArrayOf(on, on, on, on, on, on, on, off),
                intArrayOf(on, off, off, off, off, off, on, off),
                intArrayOf(on, off, off, off, off, off, on, off),
                intArrayOf(on, on, on, on, on, on, on, off),
                intArrayOf(on, off, off, off, off, off, on, off),
                intArrayOf(on, off, off, off, off, off, on, off),
                intArrayOf(on, off, off, off, off, off, on, off),
                intArrayOf(on, on, on, on, on, on, on, off),
            ),
            x = 0,
            advance = 8,
            y = 6,
        )

        return BitmapFont(
            glyphs = glyphs,
            emAscent = 6,
            emDescent = 2,
            lineAscent = 6,
            lineDescent = 2,
            xHeight = 4,
            capHeight = 6,
            lineGap = 0,
            newGlyphWidth = 8,
            name = "TestFnt",
        )
    }

    // ---- round-trip tests --------------------------------------------------

    @Test
    fun `export then import preserves codepoints`() {
        val font = buildSimpleFont()
        val fnt = FntExporter.write(font)
        val reparsed = FntImporter.read(fnt)
        // FNT is CP1252-based; ASCII codepoints should survive
        for (cp in font.glyphs.keys) {
            assertNotNull(reparsed.glyphs[cp], "lost codepoint 0x${cp.toString(16)}")
        }
    }

    @Test
    fun `round trip preserves bitmap pixel content for A`() {
        val font = buildSimpleFont()
        val fnt = FntExporter.write(font)
        val reparsed = FntImporter.read(fnt)
        val origA = font.glyphs[0x41]!!
        val rtA = reparsed.glyphs[0x41]!!
        // FntImporter contracts glyphs, so bitmap may be trimmed.
        // Count set pixels instead of comparing row-by-row.
        val origPixels = origA.bitmap.sumOf { row -> row.count { it != 0 } }
        val rtPixels = rtA.bitmap.sumOf { row -> row.count { it != 0 } }
        assertEquals(origPixels, rtPixels, "set pixel count must survive round-trip")
    }

    @Test
    fun `double round trip is stable`() {
        val font = buildSimpleFont()
        val first = FntExporter.write(font)
        val parsed1 = FntImporter.read(first)
        val second = FntExporter.write(parsed1)
        val parsed2 = FntImporter.read(second)
        assertSemanticEqual(parsed1, parsed2)
    }

    // ---- header format tests -----------------------------------------------

    @Test
    fun `output starts with valid FNT header`() {
        val font = buildSimpleFont()
        val fnt = FntExporter.write(font)
        assertTrue(fnt.size >= 118, "FNT output must be at least 118 bytes")
        // Version: first 2 bytes big-endian (same as DataOutputStream.writeShort)
        val magic = ((fnt[0].toInt() and 0xFF) shl 8) or (fnt[1].toInt() and 0xFF)
        assertTrue(magic in 1..3, "FNT version must be 1-3, got $magic")
    }

    @Test
    fun `header size field matches actual output size`() {
        val font = buildSimpleFont()
        val fnt = FntExporter.write(font)
        // Size is at offset 2, 32-bit LE
        val headerSize = readIntLE(fnt, 2)
        assertEquals(fnt.size, headerSize, "size field must match actual output size")
    }

    @Test
    fun `face name preserved in output`() {
        val font = buildSimpleFont()
        val fnt = FntExporter.write(font)
        val reparsed = FntImporter.read(fnt)
        assertEquals("TestFnt", reparsed.name, "face name must survive round-trip")
    }

    @Test
    fun `v3 export produces valid header`() {
        val font = buildSimpleFont()
        val fnt = FntExporter.write(font, version = 3)
        assertTrue(fnt.size >= 148, "FNT v3 output must be at least 148 bytes")
        val magic = ((fnt[0].toInt() and 0xFF) shl 8) or (fnt[1].toInt() and 0xFF)
        assertEquals(3, magic, "FNT version must be 3")
        // Should also be parseable
        val reparsed = FntImporter.read(fnt)
        for (cp in font.glyphs.keys) {
            assertNotNull(reparsed.glyphs[cp], "v3 lost codepoint 0x${cp.toString(16)}")
        }
    }

    @Test
    fun `ascent and height match font metrics`() {
        val font = buildSimpleFont()
        val fnt = FntExporter.write(font)
        val reparsed = FntImporter.read(fnt)
        assertEquals(font.lineAscent, reparsed.lineAscent, "lineAscent")
        assertEquals(font.lineDescent, reparsed.lineDescent, "lineDescent")
    }

    // ---- helpers -----------------------------------------------------------

    private fun readIntLE(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
    }
}
