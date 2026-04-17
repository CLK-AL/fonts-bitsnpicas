package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.PlaydateMetadataParser.PlaydateMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [PlaydateImporter] using synthetic pixel matrices.
 */
class PlaydateImporterTest {

    /**
     * Create a synthetic ARGB pixel matrix of the given dimensions.
     * By default, all pixels are opaque black (foreground/on).
     */
    private fun makePixels(
        width: Int,
        height: Int,
        argb: Int = 0xFF000000.toInt(), // opaque black
    ): Array<IntArray> {
        return Array(height) { IntArray(width) { argb } }
    }

    // ---- pixelToGray tests --------------------------------------------------

    @Test
    fun `pixelToGray transparent pixel returns 0`() {
        // Fully transparent (alpha = 0).
        assertEquals(0, PlaydateImporter.pixelToGray(0x00000000))
        // Positive int means top bit not set -> treated as transparent.
        assertEquals(0, PlaydateImporter.pixelToGray(0x7FFFFFFF))
    }

    @Test
    fun `pixelToGray opaque black returns 255`() {
        // Opaque black: R=0,G=0,B=0 -> luminance=0 < 12750 -> 255 (on).
        assertEquals(255, PlaydateImporter.pixelToGray(0xFF000000.toInt()))
    }

    @Test
    fun `pixelToGray opaque white returns 51`() {
        // Opaque white: R=255,G=255,B=255 -> luminance=25500 >= 12750 -> 51 (dim).
        assertEquals(51, PlaydateImporter.pixelToGray(0xFFFFFFFF.toInt()))
    }

    @Test
    fun `pixelToGray dark grey is on`() {
        // R=50,G=50,B=50 -> luminance = 30*50+59*50+11*50 = 5000 < 12750 -> 255.
        assertEquals(255, PlaydateImporter.pixelToGray(0xFF323232.toInt()))
    }

    // ---- import tests -------------------------------------------------------

    @Test
    fun `import with two glyphs produces correct glyph count`() {
        // 2 cells wide, 1 cell tall; cell size 8x10.
        val cw = 8
        val ch = 10
        val pixels = makePixels(cw * 2, ch)

        val metadata = PlaydateMetadata(
            name = "TestFont",
            tracking = 0,
            cellWidth = cw,
            cellHeight = ch,
            baseline = 8,
            glyphWidths = listOf(
                0x41 to 7, // 'A' -> width 7
                0x42 to 6, // 'B' -> width 6
            ),
        )

        val font = PlaydateImporter.import(metadata, pixels)
        assertEquals(2, font.glyphs.size, "Expected 2 glyphs")
        assertTrue(font.glyphs.containsKey(0x41))
        assertTrue(font.glyphs.containsKey(0x42))
    }

    @Test
    fun `import sets font metrics from metadata`() {
        val cw = 8
        val ch = 12
        val pixels = makePixels(cw, ch)

        val metadata = PlaydateMetadata(
            name = "MetricsFont",
            tracking = 2,
            cellWidth = cw,
            cellHeight = ch,
            baseline = 10,
            xHeight = 6,
            capHeight = 9,
            glyphWidths = listOf(0x41 to 7),
        )

        val font = PlaydateImporter.import(metadata, pixels)
        assertEquals("MetricsFont", font.name)
        assertEquals(10, font.emAscent, "ascent should be baseline")
        assertEquals(2, font.emDescent, "descent = cellHeight - baseline")
        assertEquals(6, font.xHeight)
        assertEquals(9, font.capHeight)
    }

    @Test
    fun `import glyph advance includes tracking`() {
        val cw = 8
        val ch = 10
        val pixels = makePixels(cw, ch)

        val metadata = PlaydateMetadata(
            tracking = 3,
            cellWidth = cw,
            cellHeight = ch,
            baseline = 8,
            glyphWidths = listOf(0x41 to 5),
        )

        val font = PlaydateImporter.import(metadata, pixels)
        val glyph = font.glyphs[0x41]
        assertNotNull(glyph)
        assertEquals(8, glyph.advance, "advance = charWidth(5) + tracking(3)")
    }

    @Test
    fun `import wraps to next row correctly`() {
        // 2 columns, 2 rows -> 4 cells.
        val cw = 4
        val ch = 4
        val pixels = makePixels(cw * 2, ch * 2)

        val metadata = PlaydateMetadata(
            cellWidth = cw,
            cellHeight = ch,
            tracking = 0,
            baseline = 3,
            glyphWidths = listOf(
                0x41 to 4, // col=0, row=0
                0x42 to 4, // col=1, row=0
                0x43 to 4, // col=0, row=1 (wraps)
            ),
        )

        val font = PlaydateImporter.import(metadata, pixels)
        assertEquals(3, font.glyphs.size, "Expected 3 glyphs")
        assertTrue(font.glyphs.containsKey(0x43), "Third glyph should wrap to row 1")
    }

    @Test
    fun `import with transparent pixels produces empty glyph after contraction`() {
        val cw = 4
        val ch = 4
        // All transparent pixels.
        val pixels = makePixels(cw, ch, argb = 0x00000000)

        val metadata = PlaydateMetadata(
            cellWidth = cw,
            cellHeight = ch,
            tracking = 0,
            baseline = 3,
            glyphWidths = listOf(0x20 to 4), // space
        )

        val font = PlaydateImporter.import(metadata, pixels)
        val glyph = font.glyphs[0x20]
        assertNotNull(glyph)
        // A fully transparent cell should contract to an empty glyph.
        assertEquals(0, glyph.height, "Transparent glyph should contract to zero height")
    }

    @Test
    fun `import with negative baseline calculates ascent correctly`() {
        val cw = 4
        val ch = 10
        val pixels = makePixels(cw, ch)

        // baseline = -2 -> ascent = ch + baseline = 10 + (-2) = 8
        val metadata = PlaydateMetadata(
            cellWidth = cw,
            cellHeight = ch,
            tracking = 0,
            baseline = -2,
            glyphWidths = listOf(0x41 to 3),
        )

        val font = PlaydateImporter.import(metadata, pixels)
        assertEquals(8, font.emAscent, "ascent = ch + negative baseline")
        assertEquals(2, font.emDescent)
    }

    @Test
    fun `import with null baseline uses cellHeight as ascent`() {
        val cw = 4
        val ch = 10
        val pixels = makePixels(cw, ch)

        val metadata = PlaydateMetadata(
            cellWidth = cw,
            cellHeight = ch,
            tracking = 0,
            baseline = null,
            glyphWidths = listOf(0x41 to 3),
        )

        val font = PlaydateImporter.import(metadata, pixels)
        assertEquals(ch, font.emAscent, "ascent = cellHeight when baseline is null")
    }
}
