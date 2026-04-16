package com.kreative.bitsnpicas.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Stage S4 differential-parity gate.
 *
 * Every fixture in the commonMain BitmapGlyphComposeTest is re-run
 * here: once through the commonMain `BitmapGlyph.compose` and once
 * through the frozen-Java `JavaLegacyAdapter.composeViaJava`. Both
 * results must be byte-exact.
 *
 * This test is what turns the KMP port from "a rewrite" into
 * "a certified byte-compatible replacement".
 */
class BitmapGlyphComposeJvmParityTest {

    @Test
    fun `single glyph — Java and Kotlin produce bit-identical output`() {
        val g = BitmapGlyph(
            bitmap = listOf(intArrayOf(0xFF, 0x00, 0xFF), intArrayOf(0x00, 0xFF, 0x00)),
            x = 0, advance = 3, y = 0,
        )
        assertParity(listOf(g))
    }

    @Test
    fun `two non-overlapping glyphs — parity`() {
        val left = BitmapGlyph(
            bitmap = listOf(intArrayOf(0xFF, 0xFF)),
            x = 0, advance = 2, y = 0,
        )
        val right = BitmapGlyph(
            bitmap = listOf(intArrayOf(0x80, 0x80)),
            x = 2, advance = 2, y = 0,
        )
        assertParity(listOf(left, right))
    }

    @Test
    fun `max-intensity overlap — parity`() {
        val a = BitmapGlyph(bitmap = listOf(intArrayOf(0x40, 0xFF)), x = 0, advance = 2, y = 0)
        val b = BitmapGlyph(bitmap = listOf(intArrayOf(0xFF, 0x40)), x = 0, advance = 2, y = 0)
        assertParity(listOf(a, b))
    }

    @Test
    fun `null-entry skipping — parity`() {
        val g = BitmapGlyph(bitmap = listOf(intArrayOf(0xFF)), x = 0, advance = 1, y = 0)
        assertParity(listOf(null, g, null))
    }

    @Test
    fun `empty input — both return null`() {
        assertNull(BitmapGlyph.compose(emptyList()))
        assertNull(JavaLegacyAdapter.composeViaJava(emptyList()))
    }

    @Test
    fun `max advance — parity`() {
        val a = BitmapGlyph(bitmap = listOf(intArrayOf(0xFF)), x = 0, advance = 2, y = 0)
        val b = BitmapGlyph(bitmap = listOf(intArrayOf(0xFF)), x = 0, advance = 7, y = 0)
        assertParity(listOf(a, b))
    }

    /** Drive both paths and assert byte-exact equality of the result. */
    private fun assertParity(input: List<BitmapGlyph?>) {
        val k = BitmapGlyph.compose(input)
        val j = JavaLegacyAdapter.composeViaJava(input)
        if (k == null || j == null) {
            assertEquals(k == null, j == null, "null-mismatch: kotlin=$k java=$j")
            return
        }
        assertEquals(j.width, k.width, "width mismatch")
        assertEquals(j.height, k.height, "height mismatch")
        assertEquals(j.x, k.x, "x mismatch")
        assertEquals(j.y, k.y, "y mismatch")
        assertEquals(j.advance, k.advance, "advance mismatch")
        for (row in 0 until k.height) {
            assertEquals(
                j.bitmap[row].toList(),
                k.bitmap[row].toList(),
                "row $row mismatch",
            )
        }
    }
}
