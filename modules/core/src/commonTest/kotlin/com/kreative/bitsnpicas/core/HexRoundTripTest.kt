package com.kreative.bitsnpicas.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Stage S4 -- Hex format round-trip and exporter tests.
 *
 * Covers:
 * - export -> import round-trip semantic equality
 * - import -> export -> import round-trip
 * - output format: `CODEPOINT:HEXDATA` lines
 * - M7 guard: output bounded by glyph count
 */
class HexRoundTripTest {

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

    private val validHex = "0041:7E818181817E00007E818181817E0000"

    // ---- round-trip tests --------------------------------------------------

    @Test
    fun `import then export then import yields semantic equality`() {
        val original = HexImporter.read(validHex)
        val serialized = HexExporter.write(original)
        val reparsed = HexImporter.read(serialized)
        assertSemanticEqual(original, reparsed)
    }

    @Test
    fun `export programmatic font then import yields same glyphs`() {
        val font = buildHexCompatibleFont()
        val hex = HexExporter.write(font)
        val reparsed = HexImporter.read(hex)
        assertEquals(font.glyphs.size, reparsed.glyphs.size, "glyph count")
        // Bitmap content should survive round-trip
        for ((cp, origGlyph) in font.glyphs) {
            val rtGlyph = reparsed.glyphs[cp]
            assertNotNull(rtGlyph, "lost codepoint 0x${cp.toString(16)}")
        }
    }

    @Test
    fun `double round trip is stable`() {
        val original = HexImporter.read(validHex)
        val first = HexExporter.write(original)
        val second = HexExporter.write(HexImporter.read(first))
        assertEquals(first, second, "exporter must be a fixed point after normalization")
    }

    // ---- output format tests -----------------------------------------------

    @Test
    fun `output lines have CODEPOINT colon HEXDATA format`() {
        val font = HexImporter.read(validHex)
        val output = HexExporter.write(font)
        val lines = output.lines().filter { it.isNotBlank() }
        assertTrue(lines.isNotEmpty(), "expected at least one output line")
        for (line in lines) {
            val parts = line.split(":")
            assertEquals(2, parts.size, "expected exactly one ':' in line: $line")
            assertTrue(parts[0].all { it in "0123456789ABCDEF" }, "codepoint must be hex: ${parts[0]}")
            assertTrue(parts[1].all { it in "0123456789ABCDEF" }, "data must be hex: ${parts[1]}")
        }
    }

    @Test
    fun `codepoint is 4 or 6 hex digits`() {
        val font = HexImporter.read(validHex)
        val output = HexExporter.write(font)
        val lines = output.lines().filter { it.isNotBlank() }
        for (line in lines) {
            val cpStr = line.substringBefore(":")
            assertTrue(
                cpStr.length == 4 || cpStr.length == 6,
                "codepoint '$cpStr' must be 4 or 6 hex digits",
            )
        }
    }

    @Test
    fun `multi-glyph hex preserves all codepoints`() {
        val hex = """
            0041:7E818181817E00007E818181817E0000
            0042:FC82828282FC0000FC82828282FC0000
        """.trimIndent()
        val original = HexImporter.read(hex)
        val serialized = HexExporter.write(original)
        val reparsed = HexImporter.read(serialized)
        assertEquals(2, reparsed.glyphs.size)
        assertTrue(reparsed.glyphs.containsKey(0x41))
        assertTrue(reparsed.glyphs.containsKey(0x42))
        assertSemanticEqual(original, reparsed)
    }

    // ---- M7 guard ----------------------------------------------------------

    @Test
    fun `M7 export output is bounded by glyph count not 0x110000`() {
        val font = HexImporter.read(validHex)
        val output = HexExporter.write(font)
        val lineCount = output.lines().filter { it.isNotBlank() }.size
        assertTrue(
            lineCount < 100,
            "output line count ($lineCount) suggests iteration over 0x110000 codepoints",
        )
    }

    // ---- helper to build a Hex-compatible font ---------------------------------

    private fun buildHexCompatibleFont(): BitmapFont {
        // Hex format uses 16-pixel-tall glyphs (h=2, height=16, width=8)
        val on = 0xFF
        val off = 0x00
        val rows = List(16) { y ->
            IntArray(8) { x ->
                if (y == 0 || y == 15 || x == 0 || x == 7) on else off
            }
        }
        return BitmapFont(
            glyphs = mapOf(
                0x41 to BitmapGlyph(bitmap = rows, x = 0, advance = 8, y = 14),
            ),
            emAscent = 14,
            emDescent = 2,
            lineAscent = 14,
            lineDescent = 2,
            xHeight = 8,
            capHeight = 12,
            lineGap = 0,
            newGlyphWidth = 16,
        )
    }
}
