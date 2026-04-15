package com.kreative.bitsnpicas.review

import com.kreative.bitsnpicas.BitmapFontGlyph
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

/**
 * Critical finding C5 - Zero-dimension glyph composition can throw.
 *
 * BitmapFontGlyph.compose() uses `<` instead of `<=` for its early-return
 * guard, which permits zero-width or zero-height output arrays. It also
 * dereferences `row.length` without null-checking the row, so a glyph
 * containing a null row in its bitmap triggers NullPointerException.
 *
 * After the fix, compose() must tolerate null rows and never NPE on
 * zero-dimensional intermediate bounds.
 */
class C5ComposeZeroDimTest {

    @Test
    fun compose_with_null_row_inside_glyph_does_not_NPE() {
        // A glyph whose bitmap contains a null row - legal per the constructor.
        val bitmap = arrayOf(
            ByteArray(4),
            null,
            ByteArray(4),
        )
        // BitmapFontGlyph(byte[][] glyph) - use the single-arg ctor.
        @Suppress("UNCHECKED_CAST")
        val g = BitmapFontGlyph(bitmap as Array<ByteArray>)

        // compose() must return something (non-null or null) rather than NPE.
        val result = try {
            BitmapFontGlyph.compose(g)
        } catch (t: NullPointerException) {
            throw AssertionError("compose() NPE'd on null-row glyph (C5)", t)
        }
        // Either non-null composed glyph or explicit null is acceptable; crash is not.
        // If result is non-null the fix must also not corrupt its backing array.
        if (result != null) {
            assertNotNull(result.glyph, "composed glyph must have a non-null bitmap")
        }
    }
}
