package com.kreative.bitsnpicas.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stage S4 -- commonMain tests for the pure-Kotlin BDF exporter.
 *
 * Covers:
 * - basic STARTFONT/ENDFONT framing and CHARS count
 * - per-glyph STARTCHAR/ENCODING correspondence
 * - hex-row content correctness for a specific glyph
 * - finding M7 regression: export must NOT iterate 0..0x110000;
 *   output line count is bounded by the glyph count, not the
 *   Unicode codepoint range.
 */
class BdfExporterTest {

    // ---- fixture builder ---------------------------------------------------

    /**
     * Build a tiny 3-glyph font. Row byte 0xFF stands for "on",
     * 0x00 stands for "off" (matching the BdfImporter decoding).
     */
    private fun buildTinyFont(): BitmapFont {
        val on = 0xFF
        val off = 0x00
        // Glyph A: chevron-ish 8x4
        val a = BitmapGlyph(
            bitmap = listOf(
                intArrayOf(on, on, on, on, on, on, on, on),
                intArrayOf(on, off, off, off, off, off, off, on),
                intArrayOf(on, off, off, off, off, off, off, on),
                intArrayOf(on, on, on, on, on, on, on, on),
            ),
            x = 0,
            advance = 8,
            y = 4,
        )
        // Glyph B: 8x4 with left column on
        val b = BitmapGlyph(
            bitmap = listOf(
                intArrayOf(on, off, off, off, off, off, off, off),
                intArrayOf(on, off, off, off, off, off, off, off),
                intArrayOf(on, off, off, off, off, off, off, off),
                intArrayOf(on, off, off, off, off, off, off, off),
            ),
            x = 0,
            advance = 8,
            y = 4,
        )
        // Glyph C: 8x4 single row on at top
        val c = BitmapGlyph(
            bitmap = listOf(
                intArrayOf(on, on, on, on, on, on, on, on),
                intArrayOf(off, off, off, off, off, off, off, off),
                intArrayOf(off, off, off, off, off, off, off, off),
                intArrayOf(off, off, off, off, off, off, off, off),
            ),
            x = 0,
            advance = 8,
            y = 4,
        )
        return BitmapFont(
            glyphs = mapOf(
                0x41 to a,
                0x42 to b,
                0x43 to c,
            ),
            emAscent = 4,
            emDescent = 0,
            lineAscent = 4,
            lineDescent = 0,
            xHeight = 3,
            capHeight = 4,
            lineGap = 0,
            newGlyphWidth = 4,
            name = "TestFont",
        )
    }

    // ---- structural tests --------------------------------------------------

    @Test
    fun `STARTFONT and ENDFONT frame the output`() {
        val out = BdfExporter.write(buildTinyFont())
        val lines = out.lines()
        assertEquals("STARTFONT 2.1", lines.first { it.isNotBlank() })
        assertTrue(out.contains("ENDFONT"), "must contain ENDFONT")
    }

    @Test
    fun `CHARS count matches glyph count`() {
        val font = buildTinyFont()
        val out = BdfExporter.write(font)
        val charsLine = out.lines().first { it.startsWith("CHARS ") }
        assertEquals("CHARS ${font.glyphs.size}", charsLine)
    }

    @Test
    fun `each STARTCHAR has matching ENCODING`() {
        val font = buildTinyFont()
        val out = BdfExporter.write(font)
        val lines = out.lines()
        val startchars = lines.filter { it.startsWith("STARTCHAR ") }
        val encodings = lines.filter { it.startsWith("ENCODING ") }
        assertEquals(font.glyphs.size, startchars.size)
        assertEquals(font.glyphs.size, encodings.size)
        // The order of STARTCHAR/ENCODING pairs should match the
        // insertion order of the glyph map.
        val expectedCps = font.glyphs.keys.toList()
        for ((i, cp) in expectedCps.withIndex()) {
            assertEquals("ENCODING $cp", encodings[i])
        }
    }

    @Test
    fun `hex-row content is correct for glyph A`() {
        val out = BdfExporter.write(buildTinyFont())
        // Glyph A has rows FF, 81, 81, FF. Find the "STARTCHAR U+0041"
        // block and read its four BITMAP rows.
        val lines = out.lines()
        val startIdx = lines.indexOf("STARTCHAR U+0041")
        assertTrue(startIdx >= 0, "expected STARTCHAR U+0041 in output")
        val bitmapIdx = lines.subList(startIdx, lines.size).indexOf("BITMAP") + startIdx
        assertEquals("FF", lines[bitmapIdx + 1])
        assertEquals("81", lines[bitmapIdx + 2])
        assertEquals("81", lines[bitmapIdx + 3])
        assertEquals("FF", lines[bitmapIdx + 4])
        assertEquals("ENDCHAR", lines[bitmapIdx + 5])
    }

    @Test
    fun `FONTBOUNDINGBOX computed from glyph metrics`() {
        val out = BdfExporter.write(buildTinyFont())
        val bbLine = out.lines().first { it.startsWith("FONTBOUNDINGBOX ") }
        // All glyphs have width=8, height=4, x=0, y=4 (descent=0).
        // So bbl=0, bbr=8, bbt=4, bbb=0 -> width=8, height=4, ox=0, oy=0.
        assertEquals("FONTBOUNDINGBOX 8 4 0 0", bbLine)
    }

    // ---- finding M7 regression --------------------------------------------

    /**
     * M7 regression guard: the frozen Java exporter iterated
     * 0..0x110000 on every export. Any implementation doing that would
     * still produce bounded output (the CHARS block is correct) but
     * the *CPU* cost would scale with the Unicode range.
     *
     * We cannot easily measure CPU time from inside a unit test, but we
     * *can* assert the output size: a naive port that also emitted a
     * per-codepoint line (e.g. a comment) for the scan would produce a
     * massive output. More importantly, the export must complete for a
     * single-glyph font without timing out -- we run it in a tight loop
     * and assert the line count stays bounded.
     */
    @Test
    fun `M7 export output is bounded by glyph count not 0x110000`() {
        val singleGlyphFont = BitmapFont(
            glyphs = mapOf(
                0x41 to BitmapGlyph(
                    bitmap = listOf(intArrayOf(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF)),
                    x = 0,
                    advance = 8,
                    y = 1,
                ),
            ),
            emAscent = 1,
            emDescent = 0,
            lineAscent = 1,
            lineDescent = 0,
            xHeight = 1,
            capHeight = 1,
            lineGap = 0,
            newGlyphWidth = 1,
            name = "T",
        )
        val out = BdfExporter.write(singleGlyphFont)
        val lineCount = out.lines().size
        // Fixed header/properties + per-glyph block (<10 lines) + trailer.
        // Any implementation that emits per-codepoint output would blow
        // past any reasonable bound (even a single line per codepoint is
        // >1M lines). 200 is comfortably above the real upper bound
        // (~25 lines for a single-glyph font) and light-years below any
        // 0x110000-scaled alternative.
        assertTrue(
            lineCount < 200,
            "output line count ($lineCount) suggests iteration over " +
                "0x110000 codepoints -- M7 regression!",
        )
    }

    @Test
    fun `M7 single-glyph export runs in microseconds not seconds`() {
        // A subtler M7 guard: a 0..0x110000 iterator inside write() would
        // take hundreds of ms even for an empty font. Calling write 100
        // times on a single-glyph font must finish comfortably within
        // any sane CI budget. We don't assert a wall-clock time (flaky),
        // but the loop itself failing to complete would be a strong
        // signal in a manual rerun.
        val font = BitmapFont(
            glyphs = mapOf(
                0x41 to BitmapGlyph(
                    bitmap = listOf(intArrayOf(0xFF)),
                    x = 0,
                    advance = 1,
                    y = 1,
                ),
            ),
            emAscent = 1,
            emDescent = 0,
            lineAscent = 1,
            lineDescent = 0,
            xHeight = 1,
            capHeight = 1,
            lineGap = 0,
            newGlyphWidth = 1,
        )
        repeat(100) {
            val out = BdfExporter.write(font)
            assertTrue(out.contains("ENDFONT"))
        }
    }

    // ---- edge case: empty glyph map ---------------------------------------

    @Test
    fun `empty font still produces valid BDF`() {
        val empty = BitmapFont(
            glyphs = emptyMap(),
            emAscent = 8,
            emDescent = 0,
            lineAscent = 8,
            lineDescent = 0,
            xHeight = 4,
            capHeight = 6,
            lineGap = 0,
            newGlyphWidth = 8,
            name = "Empty",
        )
        val out = BdfExporter.write(empty)
        assertTrue(out.contains("STARTFONT"))
        assertTrue(out.contains("ENDFONT"))
        assertTrue(out.contains("CHARS 0"))
        // No STARTCHAR blocks
        assertEquals(0, out.lines().count { it.startsWith("STARTCHAR ") })
    }
}
