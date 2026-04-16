package com.kreative.bitsnpicas.core

/**
 * Canonical glyph interface for every font flavour in the integrated
 * studio (bitmap, FIGlet, sprite, TTF outline, SVG, PNG-emoji).
 *
 * See INTEGRATION.md §4 and docs/diagrams/01-core-api.puml for the
 * shape of the full Font / Glyph / FontReader / FontWriter / PixelSink /
 * SvgSink API surface. This file lands the minimum needed for Stage S4.
 */
public interface Glyph {
    /** Horizontal advance in font pixel units. */
    public val advance: Int

    /**
     * Codepoints this glyph is keyed by.
     * A single-codepoint character → `sequenceOf(cp)`; a ligature
     * (e.g. `fi`) or a ZWJ emoji cluster → a longer sequence.
     */
    public fun codepoints(): Sequence<Int>

    /** Rasterised glyph dimensions in pixel units. */
    public val width: Int
    public val height: Int

    /** The y-offset of the glyph's baseline inside its bitmap. */
    public val baselineOffset: Int
}

/**
 * Bitmap-backed glyph. Semantically equivalent to the legacy
 * `com.kreative.bitsnpicas.BitmapFontGlyph`, ported to pure
 * Kotlin `commonMain`.
 *
 * Representation: `bitmap[row][col]` is an unsigned 8-bit intensity,
 * using platform-neutral `IntArray` row storage to avoid Java's
 * `byte` signed-arithmetic foot-guns.
 */
public class BitmapGlyph(
    /** Per-row arrays of 0..255 grayscale values. May be empty. */
    public val bitmap: List<IntArray>,
    /** Left-side bearing (x of the bitmap's left edge). */
    public val x: Int,
    override val advance: Int,
    /** Baseline position: the y-coordinate of the top-left of the bitmap. */
    public val y: Int,
    private val codepoints: IntArray = IntArray(0),
) : Glyph {

    override val width: Int = bitmap.maxOfOrNull { it.size } ?: 0
    override val height: Int = bitmap.size
    override val baselineOffset: Int = y

    override fun codepoints(): Sequence<Int> = codepoints.asSequence()

    public companion object {
        /**
         * Compose a set of bitmap glyphs into one. Mirrors the
         * fixed `BitmapFontGlyph.compose` semantics in the frozen
         * Java tree:
         *  - null glyphs and null rows are skipped (C5);
         *  - zero-width / zero-height composites return null (C5);
         *  - each output pixel is the max of contributing pixels
         *    (unsigned 0..255 comparison, matching `(row[x] & 0xFF)`).
         */
        public fun compose(glyphs: List<BitmapGlyph?>): BitmapGlyph? {
            var x0 = Int.MAX_VALUE
            var y0 = Int.MAX_VALUE
            var x1 = Int.MIN_VALUE
            var y1 = Int.MIN_VALUE
            var advance = Int.MIN_VALUE
            for (g in glyphs) {
                if (g == null || g.bitmap.isEmpty()) continue
                if (g.x < x0) x0 = g.x
                if (-g.y < y0) y0 = -g.y
                if (g.advance > advance) advance = g.advance
                if (-g.y + g.bitmap.size > y1) y1 = -g.y + g.bitmap.size
                for (row in g.bitmap) {
                    if (g.x + row.size > x1) x1 = g.x + row.size
                }
            }
            // C5: strict inequality — zero-width / zero-height composites rejected.
            if (y1 <= y0 || x1 <= x0) return null
            val out = List(y1 - y0) { IntArray(x1 - x0) }
            for (g in glyphs) {
                if (g == null || g.bitmap.isEmpty()) continue
                val bx = g.x - x0
                val by = -g.y - y0
                for (y in g.bitmap.indices) {
                    val row = g.bitmap[y]
                    for (x in row.indices) {
                        val v = row[x] and 0xFF
                        if (v > (out[by + y][bx + x] and 0xFF)) {
                            out[by + y][bx + x] = row[x]
                        }
                    }
                }
            }
            return BitmapGlyph(out, x0, advance, -y0)
        }
    }
}
