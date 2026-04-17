package com.kreative.bitsnpicas.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Stage S4 differential-parity gate for the BDF importer.
 *
 * Each valid BDF fixture is imported via both the commonMain
 * `BdfImporter.read` and the frozen Java
 * `JavaLegacyAdapter.importBdfViaJava`. The resulting `BitmapFont`
 * must agree on glyph count, dimensions, and bitmap bytes for every
 * codepoint.
 */
class BdfImporterJvmParityTest {

    // ---- parity assertion -------------------------------------------------

    private fun assertParity(input: String) {
        val kotlin = BdfImporter.read(input)
        val java = JavaLegacyAdapter.importBdfViaJava(input)

        assertEquals(java.glyphs.size, kotlin.glyphs.size, "glyph count mismatch")
        assertEquals(java.emAscent, kotlin.emAscent, "emAscent mismatch")
        assertEquals(java.emDescent, kotlin.emDescent, "emDescent mismatch")
        assertEquals(java.lineAscent, kotlin.lineAscent, "lineAscent mismatch")
        assertEquals(java.lineDescent, kotlin.lineDescent, "lineDescent mismatch")
        assertEquals(java.xHeight, kotlin.xHeight, "xHeight mismatch")
        assertEquals(java.capHeight, kotlin.capHeight, "capHeight mismatch")

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
                    "bitmap row $row mismatch at cp=$cp (0x${cp.toString(16)})",
                )
            }
        }
    }

    // ---- fixtures ---------------------------------------------------------

    @Test
    fun `single-glyph ISO10646-1 parity`() {
        val bdf = """
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
        assertParity(bdf)
    }

    @Test
    fun `multi-glyph ISO10646-1 parity`() {
        val bdf = """
            STARTFONT 2.1
            FONT test
            FONTBOUNDINGBOX 8 4 0 0
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
        assertParity(bdf)
    }

    @Test
    fun `FontSpecific PUA-offset parity`() {
        val bdf = """
            STARTFONT 2.1
            FONT test
            FONTBOUNDINGBOX 8 2 0 0
            FONT_ASCENT 2
            FONT_DESCENT 0
            CHARSET_REGISTRY "FontSpecific"
            CHARSET_ENCODING "0"
            STARTCHAR one
            ENCODING 1
            DWIDTH 8 0
            BBX 8 2 0 0
            BITMAP
            FF
            00
            ENDCHAR
            STARTCHAR two
            ENCODING 2
            DWIDTH 8 0
            BBX 8 2 0 0
            BITMAP
            00
            FF
            ENDCHAR
            ENDFONT
        """.trimIndent()
        assertParity(bdf)
    }

    @Test
    fun `wide 16px glyph parity`() {
        // 16 px wide glyph -- two hex bytes per row.
        val bdf = """
            STARTFONT 2.1
            FONT test
            FONTBOUNDINGBOX 16 2 0 0
            FONT_ASCENT 2
            FONT_DESCENT 0
            CHARSET_REGISTRY "ISO10646"
            CHARSET_ENCODING "1"
            STARTCHAR wide
            ENCODING 65
            DWIDTH 16 0
            BBX 16 2 0 0
            BITMAP
            FFFF
            8001
            ENDCHAR
            ENDFONT
        """.trimIndent()
        assertParity(bdf)
    }

    @Test
    fun `non-byte-aligned 5px width parity`() {
        // 5 px wide glyph -- still one byte per row, high bits used.
        val bdf = """
            STARTFONT 2.1
            FONT test
            FONTBOUNDINGBOX 5 3 0 0
            FONT_ASCENT 3
            FONT_DESCENT 0
            CHARSET_REGISTRY "ISO10646"
            CHARSET_ENCODING "1"
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
        assertParity(bdf)
    }

    @Test
    fun `empty bitmap glyph parity`() {
        val bdf = """
            STARTFONT 2.1
            FONT test
            FONTBOUNDINGBOX 8 4 0 0
            FONT_ASCENT 4
            FONT_DESCENT 0
            CHARSET_REGISTRY "ISO10646"
            CHARSET_ENCODING "1"
            STARTCHAR space
            ENCODING 32
            DWIDTH 8 0
            BBX 0 0 0 0
            BITMAP
            ENDCHAR
            ENDFONT
        """.trimIndent()
        assertParity(bdf)
    }
}
