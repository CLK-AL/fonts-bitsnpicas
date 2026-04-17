package com.kreative.bitsnpicas.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Stage S4 differential-parity gate for the Hex exporter.
 *
 * Exports the same font through both the commonMain [HexExporter.write]
 * and the frozen Java [JavaLegacyAdapter.exportHexViaJava], then reimports
 * both outputs via [HexImporter.read] and asserts semantic equality.
 */
class HexExporterJvmParityTest {

    private fun assertSemanticParity(font: BitmapFont) {
        val kotlinOut = HexExporter.write(font)
        val javaOut = JavaLegacyAdapter.exportHexViaJava(font)
        val kotlinParsed = HexImporter.read(kotlinOut)
        val javaParsed = HexImporter.read(javaOut)
        assertEquals(
            javaParsed.glyphs.size,
            kotlinParsed.glyphs.size,
            "glyph count mismatch between Kotlin-export and Java-export round-trips",
        )
        for ((cp, jg) in javaParsed.glyphs) {
            val kg = kotlinParsed.glyphs[cp]
            assertNotNull(kg, "Kotlin export lost codepoint 0x${cp.toString(16)}")
            assertEquals(jg.width, kg.width, "width mismatch at cp=0x${cp.toString(16)}")
            assertEquals(jg.height, kg.height, "height mismatch at cp=0x${cp.toString(16)}")
            assertEquals(jg.advance, kg.advance, "advance mismatch at cp=0x${cp.toString(16)}")
            for (row in 0 until jg.height) {
                assertEquals(
                    jg.bitmap[row].toList(),
                    kg.bitmap[row].toList(),
                    "bitmap row $row mismatch at cp=0x${cp.toString(16)}",
                )
            }
        }
    }

    private fun buildHexFont(): BitmapFont {
        val on = 0xFF
        val off = 0x00
        return BitmapFont(
            glyphs = mapOf(
                0x41 to BitmapGlyph(
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
                    y = 7,
                ),
            ),
            emAscent = 7,
            emDescent = 1,
            lineAscent = 7,
            lineDescent = 1,
            xHeight = 4,
            capHeight = 6,
            lineGap = 0,
            newGlyphWidth = 8,
        )
    }

    @Test
    fun `single-glyph parity`() {
        assertSemanticParity(buildHexFont())
    }

    @Test
    fun `multi-glyph parity`() {
        val on = 0xFF
        val off = 0x00
        val font = BitmapFont(
            glyphs = mapOf(
                0x41 to BitmapGlyph(
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
                    y = 7,
                ),
                0x42 to BitmapGlyph(
                    bitmap = listOf(
                        intArrayOf(on, on, on, on, on, on, on, off),
                        intArrayOf(on, off, off, off, off, off, on, off),
                        intArrayOf(on, on, on, on, on, on, on, off),
                        intArrayOf(on, off, off, off, off, off, on, off),
                        intArrayOf(on, off, off, off, off, off, on, off),
                        intArrayOf(on, off, off, off, off, off, on, off),
                        intArrayOf(on, on, on, on, on, on, on, off),
                        intArrayOf(off, off, off, off, off, off, off, off),
                    ),
                    x = 0,
                    advance = 8,
                    y = 7,
                ),
            ),
            emAscent = 7,
            emDescent = 1,
            lineAscent = 7,
            lineDescent = 1,
            xHeight = 4,
            capHeight = 6,
            lineGap = 0,
            newGlyphWidth = 8,
        )
        assertSemanticParity(font)
    }
}
