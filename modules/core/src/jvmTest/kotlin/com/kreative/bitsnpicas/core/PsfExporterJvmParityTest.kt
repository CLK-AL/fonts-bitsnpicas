package com.kreative.bitsnpicas.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Stage S4 differential-parity gate for the PSF exporter.
 *
 * Exports the same font through both the commonMain [PsfExporter.write]
 * and the frozen Java [JavaLegacyAdapter.exportPsfViaJava], then reimports
 * both outputs via [PsfImporter.read] and asserts semantic equality.
 *
 * The exact byte layout may differ (the Java exporter uses GlyphList-based
 * ordering and big-endian DataOutputStream, while the Kotlin port writes
 * sorted codepoints and uses ByteWriter). What matters is that parsing
 * both outputs back yields identical BitmapFonts.
 */
class PsfExporterJvmParityTest {

    /**
     * Assert parity on the codepoints that appear in the *original* font.
     *
     * The Java PSF exporter uses useLowEncoding=true by default, which
     * always emits 256 entries (indices 0..255) regardless of how many
     * glyphs the font actually has. The Kotlin port emits only the
     * codepoints present in the font map. Both are valid PSF files, but
     * the Java one carries extra blank glyphs. We therefore compare
     * only the original font's codepoints through both round-trips.
     */
    private fun assertSemanticParity(font: BitmapFont) {
        val kotlinPsf = PsfExporter.write(font)
        val javaPsf = JavaLegacyAdapter.exportPsfViaJava(font)

        val kotlinParsed = PsfImporter.read(kotlinPsf)
        val javaParsed = PsfImporter.read(javaPsf)

        // Check that every codepoint from the original font is present
        // and equal in both round-tripped outputs.
        for (cp in font.glyphs.keys) {
            val jg = javaParsed.glyphs[cp]
            assertNotNull(jg, "Java export lost codepoint 0x${cp.toString(16)}")
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

    private fun singleGlyphFont(): BitmapFont = BitmapFont(
        glyphs = mapOf(
            0x41 to BitmapGlyph(
                bitmap = listOf(
                    intArrayOf(0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF),
                    intArrayOf(0x00, 0xFF, 0x00, 0x00, 0x00, 0x00, 0xFF, 0x00),
                    intArrayOf(0x00, 0x00, 0xFF, 0x00, 0x00, 0xFF, 0x00, 0x00),
                    intArrayOf(0x00, 0x00, 0x00, 0xFF, 0xFF, 0x00, 0x00, 0x00),
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
        newGlyphWidth = 8,
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
            newGlyphWidth = 8,
        )
        assertSemanticParity(font)
    }
}
