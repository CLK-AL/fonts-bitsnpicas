package com.kreative.bitsnpicas.coverage

import com.kreative.bitsnpicas.BitmapFontGlyph
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives every branch of [BitmapFontGlyph] so the core class lands on 100%
 * line coverage.
 */
class BitmapFontGlyphCoverageTest {

    @Test
    fun default_and_array_constructor_produce_expected_metrics() {
        val empty = BitmapFontGlyph()
        assertEquals(0, empty.glyphWidth)
        assertEquals(0, empty.glyphHeight)
        assertEquals(0, empty.glyphOffset)
        assertEquals(0, empty.glyphAscent)
        assertEquals(0, empty.characterWidth)
        assertEquals(0.0, empty.glyphWidth2D)
        assertEquals(0.0, empty.glyphHeight2D)

        val g = BitmapFontGlyph(Array(2) { ByteArray(3) })
        assertEquals(3, g.glyphWidth)
        assertEquals(2, g.glyphHeight)
        assertEquals(3, g.characterWidth)
        assertEquals(2, g.glyphAscent)
        assertEquals(0, g.glyphDescent)
        assertEquals(3.0, g.glyphWidth2D)
        assertEquals(2.0, g.glyphHeight2D)
        assertEquals(0.0, g.glyphOffset2D)
        assertEquals(2.0, g.glyphAscent2D)
        assertEquals(0.0, g.glyphDescent2D)
        assertEquals(3.0, g.characterWidth2D)
    }

    @Test
    fun offset_ascent_width_constructor_sets_fields() {
        val g = BitmapFontGlyph(Array(4) { ByteArray(5) }, 1, 7, 3)
        assertEquals(1, g.x)
        assertEquals(3, g.y)
        assertEquals(7, g.characterWidth)
    }

    @Test
    fun setters_round_trip() {
        val g = BitmapFontGlyph()
        val data = Array(3) { ByteArray(3) }
        g.glyph = data
        assertTrue(g.glyph === data)
        g.setXY(2, 4)
        assertEquals(2, g.x)
        assertEquals(4, g.y)
        g.characterWidth = 9
        assertEquals(9, g.characterWidth)
        g.setCharacterWidth2D(4.2)
        assertEquals(5, g.characterWidth)
    }

    @Test
    fun paint_renders_without_error_and_returns_scaled_advance() {
        val g = TestFonts.solidBox(3, 3, 4)
        val img = BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB)
        val graphics = img.createGraphics()
        graphics.color = Color(0x80, 0x40, 0xC0, 0x80)
        val advance = g.paint(graphics, 0.0, 10.0, 2.0)
        graphics.dispose()
        assertEquals(8.0, advance)
    }

    @Test
    fun convertToPathGraph_produces_edges_for_set_pixels() {
        val g = TestFonts.solidBox(2, 2, 2)
        val pg = g.convertToPathGraph(3)
        assertTrue(pg.allEdges.isNotEmpty())
        val pg2 = g.convertToPathGraph(2, 3)
        assertTrue(pg2.allEdges.isNotEmpty())
    }

    @Test
    fun getPixel_respects_bounds() {
        val g = TestFonts.solidBox(2, 2, 2)
        // All solid pixels: iy = y + getY() = y + 2. data.length = 2, so iy in {0,1}.
        assertEquals(0xFF.toByte(), g.getPixel(0, -1))
        // y far above -> out of bounds
        assertEquals(0.toByte(), g.getPixel(0, 99))
        // y far below -> out of bounds
        assertEquals(0.toByte(), g.getPixel(0, -99))
        // x out of row -> 0
        assertEquals(0.toByte(), g.getPixel(99, -1))
    }

    @Test
    fun drawLine_plots_between_points_in_bounds() {
        // Use ascent=0 so y coord maps directly to row index (iy = y1 + gy = y1 + 0).
        val g = BitmapFontGlyph(Array(5) { ByteArray(5) }, 0, 5, 0)
        g.drawLine(0, 0, 4, 4, 0xFF.toByte())
        // Diagonal should set pixels.
        var anySet = false
        for (row in g.glyph) for (c in row) if (c != 0.toByte()) anySet = true
        assertTrue(anySet)

        // drawLine out-of-bounds shouldn't crash.
        g.drawLine(-100, -100, 100, 100, 0xFF.toByte())
        g.drawLine(-1, -1, -2, -2, 0xFF.toByte())
        // Horizontal line (y1==y2, x1==x2).
        g.drawLine(0, 0, 0, 0, 0xFF.toByte())
    }

    @Test
    fun drawRect_and_fillRect_and_invertRect_work() {
        val g = BitmapFontGlyph(Array(6) { ByteArray(6) }, 0, 6, 6)
        g.drawRect(0, 0, 3, 3, 0xFF.toByte())
        g.fillRect(1, 1, 2, 2, 0xFF.toByte())
        g.invertRect(1, 1, 2, 2)
        // Out-of-bounds coords — exercise the branches that fall through.
        g.drawRect(-100, -100, 200, 200, 0xFF.toByte())
        g.fillRect(-100, -100, 200, 200, 0xFF.toByte())
        g.invertRect(-100, -100, 200, 200)
    }

    @Test
    fun setToImage_copies_alpha_luma_pixels() {
        val img = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        img.setRGB(0, 0, 0xFF000000.toInt())  // black opaque -> 0xFF
        img.setRGB(1, 0, 0xFFFFFFFF.toInt())  // white opaque -> 0x00
        img.setRGB(0, 1, 0x00000000)          // transparent  -> 0x00
        img.setRGB(1, 1, 0xFFFF0000.toInt())  // red opaque   -> ~0xB2
        val g = BitmapFontGlyph()
        g.setToImage(0, -2, img)
        assertEquals(2, g.glyphHeight)
        assertEquals(2, g.glyphWidth)
        assertEquals(0.toByte(), g.glyph[0][1])
        assertEquals(0xFF.toByte(), g.glyph[0][0])
    }

    @Test
    fun expand_grows_in_all_directions() {
        val empty = BitmapFontGlyph()
        empty.expand(1, 2, 3, 4)
        assertEquals(3, empty.glyphWidth)
        assertEquals(4, empty.glyphHeight)

        val g = TestFonts.solidBox(2, 2, 2)
        g.expand(-2, -2, 8, 8)  // extends to left/up/right/down
        assertTrue(g.glyphWidth >= 8)

        val g2 = TestFonts.solidBox(2, 2, 2)
        // No-op case: expand into an area already contained.
        g2.expand(0, 0, 1, 1)
    }

    @Test
    fun contract_trims_empty_rows_and_columns() {
        val g = BitmapFontGlyph(Array(4) { ByteArray(4) }, 0, 4, 4)
        // Put a pixel at (1,1) only.
        g.glyph[1][1] = 0xFF.toByte()
        g.contract()
        assertEquals(1, g.glyphWidth)
        assertEquals(1, g.glyphHeight)

        val allEmpty = BitmapFontGlyph(Array(4) { ByteArray(4) }, 0, 4, 4)
        allEmpty.contract()
        assertEquals(0, allEmpty.glyphWidth)
        assertEquals(0, allEmpty.glyphHeight)

        val trulyEmpty = BitmapFontGlyph()
        trulyEmpty.contract()
        assertEquals(0, trulyEmpty.glyphWidth)

        val full = BitmapFontGlyph(Array(2) { ByteArray(2) { 0xFF.toByte() } }, 0, 2, 2)
        full.contract() // No trim needed; x/y stay.
        assertEquals(2, full.glyphWidth)
    }

    @Test
    fun compose_builds_union_and_handles_edge_cases() {
        val a = TestFonts.solidBox(2, 2, 2)
        val b = BitmapFontGlyph(Array(2) { ByteArray(2) { 0x80.toByte() } }, 1, 3, 2)
        val c: BitmapFontGlyph? = BitmapFontGlyph.compose(a, b)
        assertNotNull(c)
        assertTrue(c.glyphWidth >= 3)

        // Null glyphs and null inner array rows are ignored.
        val d = BitmapFontGlyph()
        val composedNulls = BitmapFontGlyph.compose(null, d)
        assertNull(composedNulls)

        // Row with length 0 - bounds collapse -> null.
        val zeroRow = BitmapFontGlyph(Array(1) { ByteArray(0) }, 0, 0, 0)
        val composedZero = BitmapFontGlyph.compose(zeroRow)
        assertNull(composedZero)

        // Compose with a null inner row - shouldn't NPE (C5 fix).
        val withNullRow = BitmapFontGlyph()
        val data = arrayOfNulls<ByteArray>(2).apply { this[0] = ByteArray(2) { 0xFF.toByte() }; this[1] = null }
        @Suppress("UNCHECKED_CAST")
        withNullRow.glyph = data as Array<ByteArray>
        withNullRow.setXY(0, 2)
        withNullRow.characterWidth = 2
        val composedNullRow = BitmapFontGlyph.compose(withNullRow)
        assertNotNull(composedNullRow)
    }
}
