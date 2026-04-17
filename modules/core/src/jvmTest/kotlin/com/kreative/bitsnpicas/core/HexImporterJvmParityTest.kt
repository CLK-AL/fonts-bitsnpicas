package com.kreative.bitsnpicas.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Stage S4 differential-parity gate for the Hex importer.
 *
 * Each valid Hex fixture is imported via both the commonMain
 * `HexImporter.read` and the frozen Java `JavaLegacyAdapter.importHexViaJava`.
 * The resulting `BitmapFont` must agree on glyph count, metrics,
 * dimensions, and bitmap bytes.
 */
class HexImporterJvmParityTest {

    // ---- parity assertion -----------------------------------------------

    private fun assertParity(hex: String) {
        val kotlin = HexImporter.read(hex)
        val java = JavaLegacyAdapter.importHexViaJava(hex)

        assertEquals(java.glyphs.size, kotlin.glyphs.size, "glyph count mismatch")
        assertEquals(java.emAscent, kotlin.emAscent, "emAscent mismatch")
        assertEquals(java.emDescent, kotlin.emDescent, "emDescent mismatch")
        assertEquals(java.lineAscent, kotlin.lineAscent, "lineAscent mismatch")
        assertEquals(java.lineDescent, kotlin.lineDescent, "lineDescent mismatch")
        assertEquals(java.xHeight, kotlin.xHeight, "xHeight mismatch")
        assertEquals(java.capHeight, kotlin.capHeight, "capHeight mismatch")
        assertEquals(java.lineGap, kotlin.lineGap, "lineGap mismatch")
        assertEquals(java.newGlyphWidth, kotlin.newGlyphWidth, "newGlyphWidth mismatch")

        // Compare every glyph bitmap
        for ((cp, jGlyph) in java.glyphs) {
            val kGlyph = kotlin.glyphs[cp]
                ?: error("Kotlin result missing codepoint $cp")
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

    // ---- parity tests --------------------------------------------------

    @Test
    fun `single 8x16 glyph parity`() {
        assertParity("0041:00183C66667E6600")
    }

    @Test
    fun `multiple glyphs parity`() {
        assertParity(
            """
            0041:00183C66667E6600
            0042:007C66667C66667C
            0043:003C66606060663C
            """.trimIndent()
        )
    }

    @Test
    fun `wide 16x16 glyph parity`() {
        // 64 nibbles: h=2, height=16, width=4 nibbles -> 16px wide
        val hexData = "00000018" + "003C0066" + "0066007E" + "00660000" +
            "00000018" + "003C0066" + "0066007E" + "00660000"
        assertParity("4E00:$hexData")
    }

    @Test
    fun `all-ones glyph parity`() {
        assertParity("0030:FFFFFFFFFFFFFFFF")
    }

    @Test
    fun `mixed valid and invalid lines parity`() {
        assertParity(
            """
            bad-line
            0041:00183C66667E6600
            also-bad
            0042:007C66667C66667C
            """.trimIndent()
        )
    }

    @Test
    fun `empty input produces empty font parity`() {
        val kotlin = HexImporter.read("")
        val java = JavaLegacyAdapter.importHexViaJava("")
        assertEquals(java.glyphs.size, kotlin.glyphs.size, "both should be empty")
    }
}
