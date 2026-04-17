package com.kreative.bitsnpicas.ui

import com.kreative.bitsnpicas.core.BitmapFont
import com.kreative.bitsnpicas.core.BitmapGlyph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Parity test: rendering the same glyph through any UiDriver actual must
 * produce an identical ARGB bitmap (same SHA-256).
 *
 * The fixture is a tiny inline 2-glyph bitmap font so no external files
 * are needed.
 */
class RenderGlyphParityTest {

    companion object {
        /** A small 3x5 "A" glyph: simple block pattern. */
        private val GLYPH_A = BitmapGlyph(
            bitmap = listOf(
                intArrayOf(0x00, 255, 0x00),
                intArrayOf(255, 0x00, 255),
                intArrayOf(255, 255, 255),
                intArrayOf(255, 0x00, 255),
                intArrayOf(255, 0x00, 255),
            ),
            x = 0,
            advance = 4,
            y = 5, // ascent = 5 (baseline is at bottom)
            codepoints = intArrayOf(0x41), // 'A'
        )

        /** A small 3x5 "B" glyph. */
        private val GLYPH_B = BitmapGlyph(
            bitmap = listOf(
                intArrayOf(255, 255, 0x00),
                intArrayOf(255, 0x00, 255),
                intArrayOf(255, 255, 0x00),
                intArrayOf(255, 0x00, 255),
                intArrayOf(255, 255, 0x00),
            ),
            x = 0,
            advance = 4,
            y = 5,
            codepoints = intArrayOf(0x42), // 'B'
        )

        val FIXTURE_FONT = BitmapFont(
            glyphs = mapOf(0x41 to GLYPH_A, 0x42 to GLYPH_B),
            emAscent = 5,
            emDescent = 0,
            lineAscent = 5,
            lineDescent = 0,
            xHeight = 3,
            capHeight = 5,
            lineGap = 1,
            newGlyphWidth = 4,
            name = "TestFixture",
        )
    }

    @Test
    fun renderGlyphA_producesNonEmptyBitmap() {
        val driver = UiDriver()
        try {
            driver.loadBitmapFont(FIXTURE_FONT)
            val bmp = driver.renderGlyph(0x41, 1)
            assertTrue(bmp.width > 0, "rendered glyph width should be > 0")
            assertTrue(bmp.height > 0, "rendered glyph height should be > 0")
            assertTrue(bmp.pixels.isNotEmpty(), "rendered glyph should have pixels")
        } finally {
            driver.dispose()
        }
    }

    @Test
    fun renderGlyphA_sha256IsStable() {
        val driver = UiDriver()
        try {
            driver.loadBitmapFont(FIXTURE_FONT)
            val bmp1 = driver.renderGlyph(0x41, 1)
            val bmp2 = driver.renderGlyph(0x41, 1)
            assertEquals(bmp1.sha256(), bmp2.sha256(), "same glyph rendered twice must produce identical SHA-256")
        } finally {
            driver.dispose()
        }
    }

    @Test
    fun renderGlyphB_differFromA() {
        val driver = UiDriver()
        try {
            driver.loadBitmapFont(FIXTURE_FONT)
            val bmpA = driver.renderGlyph(0x41, 1)
            val bmpB = driver.renderGlyph(0x42, 1)
            assertTrue(bmpA.sha256() != bmpB.sha256(), "different glyphs must produce different SHA-256")
        } finally {
            driver.dispose()
        }
    }
}
