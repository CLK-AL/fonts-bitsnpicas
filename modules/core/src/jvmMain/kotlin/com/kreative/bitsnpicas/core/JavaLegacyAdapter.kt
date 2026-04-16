package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.BitmapFontGlyph as JavaBitmapFontGlyph

/**
 * JVM-only adapters that bridge the frozen Java `BitmapFontGlyph`
 * into the commonMain `BitmapGlyph` API. Used by
 * BitmapGlyphComposeJvmParityTest to drive the same fixtures through
 * both implementations and assert byte-exact parity.
 *
 * This file never becomes commonMain — it exists only for the
 * duration of Stage S4 so the `jvmTest` side can perform
 * differential-parity checks. Deleted once every format module has
 * reached parity and the legacy Java profile is retired.
 */
public object JavaLegacyAdapter {

    /** Lift a commonMain BitmapGlyph into a frozen-Java BitmapFontGlyph. */
    public fun toJava(g: BitmapGlyph): JavaBitmapFontGlyph {
        val javaBytes: Array<ByteArray> = Array(g.bitmap.size) { i ->
            val row = g.bitmap[i]
            ByteArray(row.size) { j -> row[j].toByte() }
        }
        // Public 4-arg constructor: (glyph, offset=x, width=advance, ascent=y)
        return JavaBitmapFontGlyph(javaBytes, g.x, g.advance, g.y)
    }

    /** Lower a frozen-Java BitmapFontGlyph result back into a commonMain BitmapGlyph. */
    public fun fromJava(g: JavaBitmapFontGlyph?): BitmapGlyph? {
        if (g == null) return null
        val rows: Array<ByteArray> = g.glyph ?: return null
        val kRows: List<IntArray> = rows.map { row ->
            IntArray(row.size) { j -> (row[j].toInt()) and 0xFF }
        }
        return BitmapGlyph(
            bitmap = kRows,
            x = g.x,
            // BitmapFontGlyph exposes the advance via getCharacterWidth().
            advance = g.characterWidth,
            // g.y is protected — use the public getter, which returns the ascent/y.
            y = g.y,
        )
    }

    /** Compose via the frozen Java code path, returning a commonMain view. */
    public fun composeViaJava(glyphs: List<BitmapGlyph?>): BitmapGlyph? {
        val javaArr: Array<JavaBitmapFontGlyph?> = Array(glyphs.size) { i ->
            glyphs[i]?.let { toJava(it) }
        }
        return fromJava(JavaBitmapFontGlyph.compose(*javaArr))
    }
}
