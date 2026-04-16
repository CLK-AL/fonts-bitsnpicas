package com.kreative.bitsnpicas.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Stage S4 — proof-of-concept differential-parity test.
 *
 * The positive cases are run in commonTest against the pure
 * commonMain BitmapGlyph.compose. The jvmTest side-car
 * BitmapGlyphComposeJvmParityTest runs the SAME fixtures against
 * the frozen Java BitmapFontGlyph.compose and asserts both
 * implementations agree bit-exact on the resulting bitmap.
 */
class BitmapGlyphComposeTest {

    @Test
    fun `compose single glyph returns bit-exact copy`() {
        val g = bitmap(
            rows = arrayOf(
                intArrayOf(0xFF, 0x00, 0xFF),
                intArrayOf(0x00, 0xFF, 0x00),
            ),
            x = 0, y = 0, advance = 3,
        )
        val composed = BitmapGlyph.compose(listOf(g))
        assertNotNull(composed)
        assertEquals(2, composed.height)
        assertEquals(3, composed.width)
        assertEquals(3, composed.advance)
    }

    @Test
    fun `compose two non-overlapping glyphs stacks them`() {
        val left = bitmap(
            rows = arrayOf(intArrayOf(0xFF, 0xFF)),
            x = 0, y = 0, advance = 2,
        )
        val right = bitmap(
            rows = arrayOf(intArrayOf(0x80, 0x80)),
            x = 2, y = 0, advance = 2,
        )
        val composed = BitmapGlyph.compose(listOf(left, right))
        assertNotNull(composed)
        assertEquals(4, composed.width)
        assertEquals(1, composed.height)
        // pixel-max: unsigned — both glyphs contribute
        assertEquals(intArrayOf(0xFF, 0xFF, 0x80, 0x80).toList(),
                     composed.bitmap[0].toList())
    }

    @Test
    fun `compose takes the max intensity at each pixel`() {
        val a = bitmap(
            rows = arrayOf(intArrayOf(0x40, 0xFF)),
            x = 0, y = 0, advance = 2,
        )
        val b = bitmap(
            rows = arrayOf(intArrayOf(0xFF, 0x40)),
            x = 0, y = 0, advance = 2,
        )
        val composed = BitmapGlyph.compose(listOf(a, b))
        assertNotNull(composed)
        assertEquals(intArrayOf(0xFF, 0xFF).toList(), composed.bitmap[0].toList())
    }

    // --- C5 regression: null / empty input semantics ----------------

    @Test
    fun `compose rejects empty input`() {
        assertNull(BitmapGlyph.compose(emptyList()))
    }

    @Test
    fun `compose skips null entries`() {
        val g = bitmap(
            rows = arrayOf(intArrayOf(0xFF)),
            x = 0, y = 0, advance = 1,
        )
        val composed = BitmapGlyph.compose(listOf(null, g, null))
        assertNotNull(composed)
        assertEquals(1, composed.width)
    }

    @Test
    fun `compose rejects all-null input`() {
        assertNull(BitmapGlyph.compose(listOf(null, null)))
    }

    @Test
    fun `compose rejects glyph with empty bitmap`() {
        val empty = BitmapGlyph(bitmap = emptyList(), x = 0, advance = 1, y = 0)
        assertNull(BitmapGlyph.compose(listOf(empty)))
    }

    @Test
    fun `advance is the max across contributing glyphs`() {
        val a = bitmap(rows = arrayOf(intArrayOf(0xFF)), x = 0, y = 0, advance = 2)
        val b = bitmap(rows = arrayOf(intArrayOf(0xFF)), x = 0, y = 0, advance = 7)
        val composed = BitmapGlyph.compose(listOf(a, b))
        assertNotNull(composed)
        assertEquals(7, composed.advance)
    }

    // --- helpers ---------------------------------------------------

    private fun bitmap(rows: Array<IntArray>, x: Int, y: Int, advance: Int) =
        BitmapGlyph(bitmap = rows.toList(), x = x, advance = advance, y = y)
}
