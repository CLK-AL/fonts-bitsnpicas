package com.kreative.bitsnpicas.ui

import com.kreative.bitsnpicas.core.BitmapFont
import com.kreative.bitsnpicas.core.BitmapGlyph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Blue-oracle tests for the Swing-based [UiDriver] actual.
 * These verify deterministic off-screen rendering and ARGB hash stability.
 */
class SwingUiDriverTest {

    companion object {
        /** A small 3x5 "A" glyph fixture. */
        private val GLYPH_A = BitmapGlyph(
            bitmap = listOf(
                intArrayOf(0x00, 0xFF, 0x00),
                intArrayOf(0xFF, 0x00, 0xFF),
                intArrayOf(0xFF, 0xFF, 0xFF),
                intArrayOf(0xFF, 0x00, 0xFF),
                intArrayOf(0xFF, 0x00, 0xFF),
            ),
            x = 0,
            advance = 4,
            y = 5,
            codepoints = intArrayOf(0x41),
        )

        private val GLYPH_B = BitmapGlyph(
            bitmap = listOf(
                intArrayOf(0xFF, 0xFF, 0x00),
                intArrayOf(0xFF, 0x00, 0xFF),
                intArrayOf(0xFF, 0xFF, 0x00),
                intArrayOf(0xFF, 0x00, 0xFF),
                intArrayOf(0xFF, 0xFF, 0x00),
            ),
            x = 0,
            advance = 4,
            y = 5,
            codepoints = intArrayOf(0x42),
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
    fun swingRenderGlyphA_producesExpectedDimensions() {
        val driver = UiDriver()
        try {
            driver.loadBitmapFont(FIXTURE_FONT)
            val bmp = driver.renderGlyph(0x41, 1)
            assertEquals(3, bmp.width, "glyph A width at size=1 should be 3")
            assertEquals(5, bmp.height, "glyph A height at size=1 should be 5")
        } finally {
            driver.dispose()
        }
    }

    @Test
    fun swingRenderGlyphA_sha256IsStableAcrossInvocations() {
        val driver1 = UiDriver()
        val driver2 = UiDriver()
        try {
            driver1.loadBitmapFont(FIXTURE_FONT)
            driver2.loadBitmapFont(FIXTURE_FONT)
            val hash1 = driver1.renderGlyph(0x41, 1).sha256()
            val hash2 = driver2.renderGlyph(0x41, 1).sha256()
            assertEquals(hash1, hash2, "same glyph rendered by two drivers must produce identical SHA-256")
        } finally {
            driver1.dispose()
            driver2.dispose()
        }
    }

    @Test
    fun swingRenderGlyphA_size2_doublesPixelDimensions() {
        val driver = UiDriver()
        try {
            driver.loadBitmapFont(FIXTURE_FONT)
            val bmp = driver.renderGlyph(0x41, 2)
            assertEquals(6, bmp.width, "glyph A width at size=2 should be 6")
            assertEquals(10, bmp.height, "glyph A height at size=2 should be 10")
        } finally {
            driver.dispose()
        }
    }

    @Test
    fun swingRenderGlyphA_pixelsContainExpectedAlpha() {
        val driver = UiDriver()
        try {
            driver.loadBitmapFont(FIXTURE_FONT)
            val bmp = driver.renderGlyph(0x41, 1)
            // Row 0 is [0x00, 0xFF, 0x00]: centre pixel should be fully opaque black
            val centrePixel = bmp.pixels[0 * bmp.width + 1]
            val alpha = (centrePixel shr 24) and 0xFF
            assertEquals(0xFF, alpha, "centre pixel of row 0 should have alpha=255")
            // Corner pixel row0/col0 should be transparent (intensity 0)
            val cornerPixel = bmp.pixels[0]
            val cornerAlpha = (cornerPixel shr 24) and 0xFF
            assertEquals(0, cornerAlpha, "corner pixel should be transparent")
        } finally {
            driver.dispose()
        }
    }

    @Test
    fun swingRenderText_combinesTwoGlyphs() {
        val driver = UiDriver()
        try {
            driver.loadBitmapFont(FIXTURE_FONT)
            val bmp = driver.renderText("AB", 1)
            assertEquals(6, bmp.width, "AB text width should be 3+3=6")
            assertEquals(5, bmp.height, "AB text height should be 5")
            assertTrue(bmp.pixels.isNotEmpty())
        } finally {
            driver.dispose()
        }
    }
}
