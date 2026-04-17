package com.kreative.bitsnpicas.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Stage S4 -- BDF round-trip test.
 *
 * The key win of landing both the importer and exporter in commonMain:
 * `write(read(text))` and `read(write(read(text)))` should both be
 * semantically stable. We can't assert byte-exact text equality --
 * formatting (whitespace, property order, XLFD placeholders) will
 * diverge -- but the *parsed* BitmapFont on both sides must agree on
 * glyph count, dimensions, advance, offsets, and bitmap bytes.
 */
class BdfRoundTripTest {

    private fun assertSemanticEqual(a: BitmapFont, b: BitmapFont) {
        assertEquals(a.glyphs.size, b.glyphs.size, "glyph count mismatch")
        for ((cp, ag) in a.glyphs) {
            val bg = b.glyphs[cp]
            assertNotNull(bg, "round-trip lost codepoint 0x${cp.toString(16)}")
            assertEquals(ag.width, bg.width, "width mismatch at cp=$cp")
            assertEquals(ag.height, bg.height, "height mismatch at cp=$cp")
            assertEquals(ag.advance, bg.advance, "advance mismatch at cp=$cp")
            assertEquals(ag.x, bg.x, "x mismatch at cp=$cp")
            assertEquals(ag.y, bg.y, "y mismatch at cp=$cp")
            for (row in 0 until ag.height) {
                assertEquals(
                    ag.bitmap[row].toList(),
                    bg.bitmap[row].toList(),
                    "bitmap row $row mismatch at cp=$cp",
                )
            }
        }
    }

    // ---- fixtures ----------------------------------------------------------

    private val validBdf = """
        STARTFONT 2.1
        FONT -Test-Fixed-Medium-R-Normal--8-80-75-75-C-80-ISO10646-1
        SIZE 8 75 75
        FONTBOUNDINGBOX 8 8 0 0
        STARTPROPERTIES 4
        FAMILY_NAME "TestFixed"
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

    // ---- tests -------------------------------------------------------------

    @Test
    fun `read then write then read yields semantic equality`() {
        val original = BdfImporter.read(validBdf)
        val serialized = BdfExporter.write(original)
        val reparsed = BdfImporter.read(serialized)
        assertSemanticEqual(original, reparsed)
    }

    @Test
    fun `multi-glyph round trip preserves all codepoints`() {
        val bdf = """
            STARTFONT 2.1
            FONT test
            FONTBOUNDINGBOX 8 4 0 0
            FAMILY_NAME "Multi"
            FONT_ASCENT 4
            FONT_DESCENT 0
            CHARSET_REGISTRY "ISO10646"
            CHARSET_ENCODING "1"
            CHARS 3
            STARTCHAR A
            ENCODING 65
            DWIDTH 8 0
            BBX 8 4 0 0
            BITMAP
            FF
            81
            81
            FF
            ENDCHAR
            STARTCHAR B
            ENCODING 66
            DWIDTH 8 0
            BBX 8 4 0 0
            BITMAP
            FE
            82
            82
            FE
            ENDCHAR
            STARTCHAR C
            ENCODING 67
            DWIDTH 8 0
            BBX 8 4 0 0
            BITMAP
            7E
            80
            80
            7E
            ENDCHAR
            ENDFONT
        """.trimIndent()
        val original = BdfImporter.read(bdf)
        val serialized = BdfExporter.write(original)
        val reparsed = BdfImporter.read(serialized)
        assertSemanticEqual(original, reparsed)
        assertEquals(3, reparsed.glyphs.size)
        assertTrue(reparsed.glyphs.containsKey(0x41))
        assertTrue(reparsed.glyphs.containsKey(0x42))
        assertTrue(reparsed.glyphs.containsKey(0x43))
    }

    @Test
    fun `round trip preserves bitmap byte content`() {
        val original = BdfImporter.read(validBdf)
        val serialized = BdfExporter.write(original)
        val reparsed = BdfImporter.read(serialized)
        val origA = original.glyphs[0x41]!!
        val rtA = reparsed.glyphs[0x41]!!
        for (row in 0 until origA.height) {
            assertEquals(
                origA.bitmap[row].toList(),
                rtA.bitmap[row].toList(),
                "row $row diverged on round trip",
            )
        }
    }

    @Test
    fun `double round trip is a fixed point`() {
        // write -> read -> write should produce the same text as the
        // first write (the model normalises toward the exporter's
        // canonical output).
        val original = BdfImporter.read(validBdf)
        val first = BdfExporter.write(original)
        val second = BdfExporter.write(BdfImporter.read(first))
        assertEquals(first, second, "exporter must be a fixed point after the first normalisation")
    }

    @Test
    fun `non-byte-aligned width round-trips correctly`() {
        // 5-pixel-wide glyph -- the exporter pads to a byte boundary
        // (F8 for five 1-bits), the importer decodes back to 8 columns
        // of width 5. We re-export and re-import to check the bitmap
        // bytes survive the second pass too.
        val bdf = """
            STARTFONT 2.1
            FONT test
            FONTBOUNDINGBOX 5 3 0 0
            FONT_ASCENT 3
            FONT_DESCENT 0
            CHARSET_REGISTRY "ISO10646"
            STARTCHAR narrow
            ENCODING 65
            DWIDTH 5 0
            BBX 5 3 0 0
            BITMAP
            F8
            88
            F8
            ENDCHAR
            ENDFONT
        """.trimIndent()
        val original = BdfImporter.read(bdf)
        val reparsed = BdfImporter.read(BdfExporter.write(original))
        assertSemanticEqual(original, reparsed)
    }
}
