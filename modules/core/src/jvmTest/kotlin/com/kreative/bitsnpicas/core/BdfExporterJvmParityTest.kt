package com.kreative.bitsnpicas.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Stage S4 differential-parity gate for the BDF exporter.
 *
 * For each fixture we export via both the commonMain `BdfExporter.write`
 * and the frozen Java `JavaLegacyAdapter.exportBdfViaJava`, then reparse
 * *both* outputs via the commonMain `BdfImporter.read`. The resulting
 * `BitmapFont`s must agree on glyph count, dimensions, advance, offsets,
 * and bitmap bytes.
 *
 * Semantic equality (not byte-exact text) is the correct gate: the two
 * exporters differ in whitespace, in the XLFD FONT line's placeholder
 * fields, and in the order of some STARTPROPERTIES entries. What the
 * format promises is round-trippable data, not byte-identical serialisation.
 */
class BdfExporterJvmParityTest {

    // ---- parity assertion --------------------------------------------------

    private fun assertSemanticParity(font: BitmapFont) {
        val kotlinOut = BdfExporter.write(font)
        val javaOut = JavaLegacyAdapter.exportBdfViaJava(font)
        val kotlinParsed = BdfImporter.read(kotlinOut)
        val javaParsed = BdfImporter.read(javaOut)
        assertEquals(
            javaParsed.glyphs.size,
            kotlinParsed.glyphs.size,
            "glyph count mismatch between Kotlin-export and Java-export round-trips",
        )
        for ((cp, jg) in javaParsed.glyphs) {
            val kg = kotlinParsed.glyphs[cp]
            assertNotNull(kg, "Kotlin export lost codepoint 0x${cp.toString(16)}")
            assertEquals(jg.width, kg.width, "width mismatch at cp=$cp")
            assertEquals(jg.height, kg.height, "height mismatch at cp=$cp")
            assertEquals(jg.advance, kg.advance, "advance mismatch at cp=$cp")
            assertEquals(jg.x, kg.x, "x mismatch at cp=$cp")
            assertEquals(jg.y, kg.y, "y mismatch at cp=$cp")
            for (row in 0 until jg.height) {
                assertEquals(
                    jg.bitmap[row].toList(),
                    kg.bitmap[row].toList(),
                    "bitmap row $row mismatch at cp=$cp",
                )
            }
        }
    }

    // ---- fixtures ----------------------------------------------------------

    private fun singleGlyphFont(): BitmapFont = BitmapFont(
        glyphs = mapOf(
            0x41 to BitmapGlyph(
                bitmap = listOf(
                    intArrayOf(0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF),
                    intArrayOf(0x00, 0xFF, 0x00, 0x00, 0x00, 0x00, 0xFF, 0x00),
                    intArrayOf(0x00, 0x00, 0xFF, 0x00, 0x00, 0xFF, 0x00, 0x00),
                    intArrayOf(0x00, 0x00, 0x00, 0xFF, 0xFF, 0x00, 0x00, 0x00),
                    intArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
                    intArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
                    intArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
                    intArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
                ),
                x = 0,
                advance = 8,
                y = 8,
            ),
        ),
        emAscent = 8,
        emDescent = 0,
        lineAscent = 8,
        lineDescent = 0,
        xHeight = 4,
        capHeight = 6,
        lineGap = 0,
        newGlyphWidth = 8,
        name = "Parity",
    )

    @Test
    fun `single-glyph parity`() {
        assertSemanticParity(singleGlyphFont())
    }

    @Test
    fun `multi-glyph parity`() {
        val font = BitmapFont(
            glyphs = mapOf(
                0x41 to BitmapGlyph(
                    bitmap = listOf(
                        intArrayOf(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF),
                        intArrayOf(0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF),
                        intArrayOf(0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF),
                        intArrayOf(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF),
                    ),
                    x = 0,
                    advance = 8,
                    y = 4,
                ),
                0x42 to BitmapGlyph(
                    bitmap = listOf(
                        intArrayOf(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x00),
                        intArrayOf(0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0x00),
                        intArrayOf(0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0x00),
                        intArrayOf(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x00),
                    ),
                    x = 0,
                    advance = 8,
                    y = 4,
                ),
                0x43 to BitmapGlyph(
                    bitmap = listOf(
                        intArrayOf(0x00, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x00),
                        intArrayOf(0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
                        intArrayOf(0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
                        intArrayOf(0x00, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x00),
                    ),
                    x = 0,
                    advance = 8,
                    y = 4,
                ),
            ),
            emAscent = 4,
            emDescent = 0,
            lineAscent = 4,
            lineDescent = 0,
            xHeight = 3,
            capHeight = 4,
            lineGap = 0,
            newGlyphWidth = 4,
            name = "MultiParity",
        )
        assertSemanticParity(font)
    }

    @Test
    fun `16px wide glyph parity`() {
        val font = BitmapFont(
            glyphs = mapOf(
                0x41 to BitmapGlyph(
                    bitmap = listOf(
                        IntArray(16) { 0xFF },
                        intArrayOf(
                            0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF,
                        ),
                    ),
                    x = 0,
                    advance = 16,
                    y = 2,
                ),
            ),
            emAscent = 2,
            emDescent = 0,
            lineAscent = 2,
            lineDescent = 0,
            xHeight = 1,
            capHeight = 2,
            lineGap = 0,
            newGlyphWidth = 16,
            name = "Wide",
        )
        assertSemanticParity(font)
    }

    @Test
    fun `non-byte-aligned 5px width parity`() {
        val font = BitmapFont(
            glyphs = mapOf(
                0x41 to BitmapGlyph(
                    bitmap = listOf(
                        intArrayOf(0xFF, 0xFF, 0xFF, 0xFF, 0xFF),
                        intArrayOf(0xFF, 0x00, 0x00, 0x00, 0xFF),
                        intArrayOf(0xFF, 0xFF, 0xFF, 0xFF, 0xFF),
                    ),
                    x = 0,
                    advance = 5,
                    y = 3,
                ),
            ),
            emAscent = 3,
            emDescent = 0,
            lineAscent = 3,
            lineDescent = 0,
            xHeight = 2,
            capHeight = 3,
            lineGap = 0,
            newGlyphWidth = 5,
            name = "Narrow",
        )
        assertSemanticParity(font)
    }
}
