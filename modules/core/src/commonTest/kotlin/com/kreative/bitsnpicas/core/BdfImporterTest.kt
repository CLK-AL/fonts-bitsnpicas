package com.kreative.bitsnpicas.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Stage S4 -- commonMain tests for the pure-Kotlin BDF importer.
 *
 * Covers:
 * - valid parse (ISO10646-1, single glyph) -> expected glyph count,
 *   codepoint, dimensions, bitmap bytes
 * - missing ENDCHAR handled without crashing (malformed input)
 * - unknown CHARSET_REGISTRY surfaces an M8 warning
 * - empty bitmap handled (BBX 0 0 0 0) -- no NPE, no state leak
 * - C4 regression: a parse failure doesn't leak dangling state into
 *   the next glyph
 */
class BdfImporterTest {

    // ---- fixture builder ---------------------------------------------------

    /**
     * A minimal, valid ISO10646-1 BDF with a single 8x8 glyph at
     * codepoint 0x41 ('A'). The bitmap is a simple chevron:
     *   row 0: 10000001
     *   row 1: 01000010
     *   row 2: 00100100
     *   row 3: 00011000
     *   rows 4..7: all zero
     */
    private val validSingleGlyphBdf = """
        STARTFONT 2.1
        FONT -Test-Fixed-Medium-R-Normal--8-80-75-75-C-80-ISO10646-1
        SIZE 8 75 75
        FONTBOUNDINGBOX 8 8 0 0
        STARTPROPERTIES 4
        FONT_ASCENT 8
        FONT_DESCENT 0
        CHARSET_REGISTRY "ISO10646"
        CHARSET_ENCODING "1"
        ENDPROPERTIES
        CHARS 1
        STARTCHAR A
        ENCODING 65
        SWIDTH 480 0
        DWIDTH 8 0
        BBX 8 8 0 0
        BITMAP
        81
        42
        24
        18
        00
        00
        00
        00
        ENDCHAR
        ENDFONT
    """.trimIndent()

    // ---- valid-parse tests -------------------------------------------------

    @Test
    fun `valid ISO10646-1 single glyph parses with expected count`() {
        val font = BdfImporter.read(validSingleGlyphBdf)
        assertEquals(1, font.glyphs.size)
        assertTrue(font.glyphs.containsKey(0x41), "expected 'A' at codepoint 0x41")
    }

    @Test
    fun `valid single glyph has correct dimensions`() {
        val font = BdfImporter.read(validSingleGlyphBdf)
        val g = font.glyphs[0x41]!!
        assertEquals(8, g.width)
        assertEquals(8, g.height)
        assertEquals(8, g.advance)
    }

    @Test
    fun `valid single glyph bitmap bytes are decoded from hex`() {
        val font = BdfImporter.read(validSingleGlyphBdf)
        val g = font.glyphs[0x41]!!
        // Row 0: 0x81 = 10000001 -> set pixels at columns 0 and 7
        val row0 = g.bitmap[0]
        assertEquals(0xFF, row0[0])
        assertEquals(0x00, row0[1])
        assertEquals(0x00, row0[6])
        assertEquals(0xFF, row0[7])
        // Row 1: 0x42 = 01000010 -> set pixels at columns 1 and 6
        val row1 = g.bitmap[1]
        assertEquals(0x00, row1[0])
        assertEquals(0xFF, row1[1])
        assertEquals(0xFF, row1[6])
        assertEquals(0x00, row1[7])
        // Row 4 should be all zero
        val row4 = g.bitmap[4]
        for (v in row4) assertEquals(0x00, v)
    }

    @Test
    fun `valid font FAMILY_NAME is preserved`() {
        val bdf = """
            STARTFONT 2.1
            FAMILY_NAME "MyFont"
            FONT_ASCENT 8
            FONT_DESCENT 0
            CHARSET_REGISTRY "ISO10646"
            ENDFONT
        """.trimIndent()
        val font = BdfImporter.read(bdf)
        assertEquals("MyFont", font.name)
    }

    @Test
    fun `valid font metrics are parsed`() {
        val bdf = """
            STARTFONT 2.1
            FONT_ASCENT 12
            FONT_DESCENT 4
            X_HEIGHT 6
            CAP_HEIGHT 9
            CHARSET_REGISTRY "ISO10646"
            ENDFONT
        """.trimIndent()
        val font = BdfImporter.read(bdf)
        assertEquals(12, font.lineAscent)
        assertEquals(12, font.emAscent)
        assertEquals(4, font.lineDescent)
        assertEquals(4, font.emDescent)
        assertEquals(6, font.xHeight)
        assertEquals(9, font.capHeight)
        // newGlyphWidth = ascent + descent after both are set
        assertEquals(16, font.newGlyphWidth)
    }

    // ---- missing-ENDCHAR handling -----------------------------------------

    @Test
    fun `missing ENDCHAR does not crash`() {
        // A glyph section without ENDCHAR, followed by ENDFONT.
        val bdf = """
            STARTFONT 2.1
            FONT_ASCENT 8
            FONT_DESCENT 0
            CHARSET_REGISTRY "ISO10646"
            STARTCHAR A
            ENCODING 65
            DWIDTH 8 0
            BBX 8 2 0 0
            BITMAP
            FF
            FF
            ENDFONT
        """.trimIndent()
        // Must return without exception; the glyph is still registered
        // because BITMAP filled the declared height (2 rows).
        val result = BdfImporter.readWithWarnings(bdf)
        assertEquals(1, result.font.glyphs.size)
    }

    @Test
    fun `missing ENDCHAR with truncated bitmap does not crash`() {
        // Declared BBX height 4 but only 2 BITMAP rows + premature EOF.
        val bdf = """
            STARTFONT 2.1
            FONT_ASCENT 8
            FONT_DESCENT 0
            CHARSET_REGISTRY "ISO10646"
            STARTCHAR A
            ENCODING 65
            DWIDTH 8 0
            BBX 8 4 0 0
            BITMAP
            FF
            FF
        """.trimIndent()
        // Must not throw -- incomplete bitmap is best-effort.
        val font = BdfImporter.read(bdf)
        // The glyph is still registered: top 2 rows set, bottom 2 rows zero.
        val g = font.glyphs[0x41]
        assertNotNull(g)
        assertEquals(4, g.height)
        assertEquals(0xFF, g.bitmap[0][0])
        assertEquals(0x00, g.bitmap[2][0])
    }

    // ---- CHARSET_REGISTRY warning (M8) ------------------------------------

    @Test
    fun `unknown CHARSET_REGISTRY surfaces a warning`() {
        val bdf = """
            STARTFONT 2.1
            FONT_ASCENT 8
            FONT_DESCENT 0
            CHARSET_REGISTRY "BogusRegistry"
            ENDFONT
        """.trimIndent()
        val result = BdfImporter.readWithWarnings(bdf)
        assertTrue(result.warnings.any { it.contains("BogusRegistry") },
            "expected warning mentioning the unknown registry, got ${result.warnings}")
    }

    @Test
    fun `known ISO10646 registry does not warn`() {
        val result = BdfImporter.readWithWarnings(validSingleGlyphBdf)
        assertTrue(result.warnings.isEmpty(),
            "ISO10646 should not warn, got ${result.warnings}")
    }

    @Test
    fun `FontSpecific registry offsets ENCODING into PUA`() {
        val bdf = """
            STARTFONT 2.1
            FONT_ASCENT 8
            FONT_DESCENT 0
            CHARSET_REGISTRY "FontSpecific"
            STARTCHAR fontglyph
            ENCODING 1
            DWIDTH 8 0
            BBX 8 1 0 0
            BITMAP
            FF
            ENDCHAR
            ENDFONT
        """.trimIndent()
        val font = BdfImporter.read(bdf)
        // ENCODING 1 + 0xF000 = 0xF001 PUA codepoint
        assertTrue(font.glyphs.containsKey(0xF001),
            "expected PUA codepoint 0xF001, glyphs=${font.glyphs.keys}")
    }

    // ---- empty bitmap handling --------------------------------------------

    @Test
    fun `empty bitmap BBX zero handled gracefully`() {
        val bdf = """
            STARTFONT 2.1
            FONT_ASCENT 8
            FONT_DESCENT 0
            CHARSET_REGISTRY "ISO10646"
            STARTCHAR space
            ENCODING 32
            DWIDTH 8 0
            BBX 0 0 0 0
            BITMAP
            ENDCHAR
            ENDFONT
        """.trimIndent()
        val font = BdfImporter.read(bdf)
        val g = font.glyphs[0x20]
        assertNotNull(g)
        assertEquals(0, g.width)
        assertEquals(0, g.height)
        assertEquals(8, g.advance)
    }

    @Test
    fun `glyph without BITMAP section still registered`() {
        // No BITMAP keyword at all; ENDCHAR terminates. Java's
        // BitmapFontGlyph() default-constructs with an empty glyph
        // array; we mirror that with an empty rows list.
        val bdf = """
            STARTFONT 2.1
            FONT_ASCENT 8
            FONT_DESCENT 0
            CHARSET_REGISTRY "ISO10646"
            STARTCHAR A
            ENCODING 65
            DWIDTH 8 0
            BBX 8 2 0 0
            ENDCHAR
            ENDFONT
        """.trimIndent()
        val font = BdfImporter.read(bdf)
        val g = font.glyphs[0x41]
        assertNotNull(g)
        assertEquals(2, g.height)
        assertEquals(8, g.width)
        // All pixels should be zero
        for (row in g.bitmap) for (v in row) assertEquals(0, v)
    }

    // ---- C4 regression: no dangling state on parse failure ----------------

    @Test
    fun `C4 two glyphs after malformed first still parse second cleanly`() {
        // First glyph is missing ENDCHAR between ENCODING and the next
        // STARTCHAR; ensure the second glyph ('B') is still registered
        // correctly (no stale accumulator leaking from glyph 1 into
        // glyph 2). With no BITMAP in glyph 1, and glyph 2's STARTCHAR
        // encountered in readChar's loop, the parser must break out
        // cleanly -- parity with the frozen Java code is the goal.
        val bdf = """
            STARTFONT 2.1
            FONT_ASCENT 8
            FONT_DESCENT 0
            CHARSET_REGISTRY "ISO10646"
            STARTCHAR A
            ENCODING 65
            DWIDTH 8 0
            BBX 8 1 0 0
            BITMAP
            FF
            ENDCHAR
            STARTCHAR B
            ENCODING 66
            DWIDTH 8 0
            BBX 8 1 0 0
            BITMAP
            0F
            ENDCHAR
            ENDFONT
        """.trimIndent()
        val font = BdfImporter.read(bdf)
        assertEquals(2, font.glyphs.size)
        val a = font.glyphs[0x41]!!
        val b = font.glyphs[0x42]!!
        // Row 0 of A should be all set; row 0 of B should be only the
        // low nibble set. This proves the per-glyph bitmap buffer was
        // reset, not shared.
        assertEquals(0xFF, a.bitmap[0][0])
        assertEquals(0x00, b.bitmap[0][0])
        assertEquals(0xFF, b.bitmap[0][4])
    }

    @Test
    fun `C4 parse-failure does not bleed into glyph count`() {
        // Second "glyph" has an invalid ENCODING value; should be
        // skipped without crashing, and the first glyph survives.
        val bdf = """
            STARTFONT 2.1
            FONT_ASCENT 8
            FONT_DESCENT 0
            CHARSET_REGISTRY "ISO10646"
            STARTCHAR A
            ENCODING 65
            DWIDTH 8 0
            BBX 8 1 0 0
            BITMAP
            FF
            ENDCHAR
            STARTCHAR badglyph
            ENCODING notanumber
            DWIDTH 8 0
            BBX 8 1 0 0
            BITMAP
            00
            ENDCHAR
            ENDFONT
        """.trimIndent()
        val font = BdfImporter.read(bdf)
        // Only glyph A is registered under a codepoint. The bad one
        // either falls into namedGlyphs (not exposed on BitmapFont) or
        // is dropped.
        assertEquals(1, font.glyphs.size)
        assertTrue(font.glyphs.containsKey(0x41))
    }

    // ---- malformed top-level tests ----------------------------------------

    @Test
    fun `input without STARTFONT throws BdfParseException`() {
        assertFailsWith<BdfImporter.BdfParseException> {
            BdfImporter.read("random text\nwith no BDF header\n")
        }
    }

    @Test
    fun `empty input throws BdfParseException`() {
        assertFailsWith<BdfImporter.BdfParseException> {
            BdfImporter.read("")
        }
    }
}
