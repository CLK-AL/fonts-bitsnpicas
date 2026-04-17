package com.kreative.bitsnpicas.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stage S4 -- commonMain tests for the pure-Kotlin Hex importer.
 *
 * Tests inline hex content (no file I/O), including:
 * - valid multi-glyph parse with correct dimensions + bitmap
 * - m11 regression: zero-length hex data does not crash
 * - round-trip consistency (re-encoding from bitmap back to hex)
 * - malformed lines are silently skipped
 */
class HexImporterTest {

    // ---- basic parsing -------------------------------------------------

    @Test
    fun `single 8x16 glyph parses correctly`() {
        // U+0041 'A': 16 hex nibbles -> h=1, height=8, width=2 nibbles
        // 16 nibbles / 8 height = 2 width -> glyph is 8px wide (2*4)
        val hex = "0041:00183C66667E6600"
        val font = HexImporter.read(hex)
        assertEquals(1, font.glyphs.size, "should have one glyph")
        assertTrue(font.glyphs.containsKey(0x41), "should contain codepoint 0x41")
        val g = font.glyphs[0x41]!!
        assertEquals(8, g.height, "height should be 8 (h=1, height=h*8)")
        assertEquals(8, g.advance, "advance = width*4 = 2*4 = 8")
    }

    @Test
    fun `multiple glyphs parsed from multiline input`() {
        val hex = """
            0041:00183C66667E6600
            0042:007C66667C66667C
            0043:003C66606060663C
        """.trimIndent()
        val font = HexImporter.read(hex)
        assertEquals(3, font.glyphs.size, "should have three glyphs")
        assertTrue(font.glyphs.containsKey(0x41))
        assertTrue(font.glyphs.containsKey(0x42))
        assertTrue(font.glyphs.containsKey(0x43))
    }

    @Test
    fun `wide 16x16 glyph parses correctly`() {
        // 64 hex nibbles -> h=2, height=16, width=4 nibbles -> 16px wide (4*4)
        // 16 rows x 4 nibbles/row = 64 nibbles total
        val hexData = "00000018" + "003C0066" + "0066007E" + "00660000" +
            "00000018" + "003C0066" + "0066007E" + "00660000"
        val hex = "4E00:$hexData"
        val font = HexImporter.read(hex)
        assertEquals(1, font.glyphs.size)
        val g = font.glyphs[0x4E00]!!
        assertEquals(16, g.height, "height should be 16 (h=2)")
        assertEquals(16, g.advance, "advance = 4*4 = 16")
    }

    // ---- bitmap content verification -----------------------------------

    @Test
    fun `bitmap content matches hex encoding`() {
        // Single row byte FF -> all 8 pixels set
        // 16 nibbles of all-F means every pixel is on
        val hex = "0030:FFFFFFFFFFFFFFFF"
        val font = HexImporter.read(hex)
        val g = font.glyphs[0x30]!!
        // All pixels should be 0xFF
        for (y in 0 until g.height) {
            for (x in 0 until g.bitmap[y].size) {
                assertEquals(0xFF, g.bitmap[y][x], "pixel ($x,$y) should be 0xFF")
            }
        }
    }

    @Test
    fun `first nibble 0 sets no pixels`() {
        // First row starts with 0 -> first 4 pixels should be 0
        val hex = "0031:00183C66667E6600"
        val font = HexImporter.read(hex)
        val g = font.glyphs[0x31]!!
        // First nibble of row 0 is '0' -> pixels [0],[1],[2],[3] should all be 0
        assertEquals(0, g.bitmap[0][0])
        assertEquals(0, g.bitmap[0][1])
        assertEquals(0, g.bitmap[0][2])
        assertEquals(0, g.bitmap[0][3])
    }

    // ---- m11 regression: zero-length hex data --------------------------

    @Test
    fun `m11 zero-length hex data does not crash`() {
        val hex = "0041:"
        val font = HexImporter.read(hex)
        // Should produce an empty font (the line is skipped gracefully)
        assertEquals(0, font.glyphs.size, "zero-length hex should be skipped")
    }

    @Test
    fun `m11 very short hex data does not crash`() {
        // A single nibble: h = floor(sqrt(5))+2 / 4 = (2+2)/4 = 1
        // height = 8, width = 1/8 = 0 -> skipped (width < 1)
        val hex = "0041:F"
        val font = HexImporter.read(hex)
        assertEquals(0, font.glyphs.size, "too-short hex should be skipped")
    }

    // ---- malformed input handling --------------------------------------

    @Test
    fun `malformed lines are skipped`() {
        val hex = """
            not-a-hex-line
            ZZZZ:FFFFFFFFFFFFFFFF
            0041:00183C66667E6600
            no-colon
            :00183C66667E6600
        """.trimIndent()
        val font = HexImporter.read(hex)
        // Only 0041 should parse; ZZZZ is not valid hex
        assertEquals(1, font.glyphs.size)
        assertTrue(font.glyphs.containsKey(0x41))
    }

    @Test
    fun `invalid hex nibbles in data cause line to be skipped`() {
        // 'G' is not a valid hex digit
        val hex = "0041:00183C6G667E6600"
        val font = HexImporter.read(hex)
        assertEquals(0, font.glyphs.size)
    }

    // ---- font metrics --------------------------------------------------

    @Test
    fun `font metrics reflect maxh for 8x16 glyphs`() {
        val hex = "0041:00183C66667E6600"
        val font = HexImporter.read(hex)
        // h=1, maxh=1
        assertEquals(7, font.emAscent, "emAscent = maxh * 7")
        assertEquals(1, font.emDescent, "emDescent = maxh")
        assertEquals(7, font.lineAscent)
        assertEquals(1, font.lineDescent)
        assertEquals(5, font.xHeight, "xHeight = 5 when maxh == 1")
        assertEquals(7, font.capHeight, "capHeight = 7 when maxh == 1")
        assertEquals(0, font.lineGap)
        assertEquals(8, font.newGlyphWidth, "newGlyphWidth = maxh * 8")
    }

    @Test
    fun `font metrics reflect maxh for 16x16 glyphs`() {
        // 64 nibbles -> h=2
        val hexData = "0" .repeat(64)
        // Need non-zero data for the glyph to be valid; all-zero is fine as long as parsing succeeds
        val hex = "0041:${hexData.replaceRange(0, 1, "F")}"
        val font = HexImporter.read(hex)
        // h=2, maxh=2
        assertEquals(14, font.emAscent, "emAscent = 2 * 7")
        assertEquals(2, font.emDescent, "emDescent = 2")
        assertEquals(8, font.xHeight, "xHeight = maxh * 4 when maxh > 1")
        assertEquals(10, font.capHeight, "capHeight = maxh * 5 when maxh > 1")
        assertEquals(16, font.newGlyphWidth, "newGlyphWidth = maxh * 8")
    }

    // ---- round-trip consistency ----------------------------------------

    @Test
    fun `round-trip consistency - re-encode bitmap back to hex`() {
        val original = "0041:00183C66667E6600"
        val font = HexImporter.read(original)
        val g = font.glyphs[0x41]!!

        // Re-encode: each row, each group of 4 pixels -> 1 hex nibble
        val sb = StringBuilder()
        for (y in 0 until g.height) {
            val row = g.bitmap[y]
            var x = 0
            while (x < row.size) {
                var nibble = 0
                if (row[x] != 0) nibble = nibble or 8
                if (row[x + 1] != 0) nibble = nibble or 4
                if (row[x + 2] != 0) nibble = nibble or 2
                if (row[x + 3] != 0) nibble = nibble or 1
                sb.append(nibble.toString(16).uppercase())
                x += 4
            }
        }
        assertEquals("00183C66667E6600", sb.toString(), "round-trip should match original hex data")
    }

    // ---- empty input ---------------------------------------------------

    @Test
    fun `empty input produces empty font`() {
        val font = HexImporter.read("")
        assertEquals(0, font.glyphs.size)
    }
}
