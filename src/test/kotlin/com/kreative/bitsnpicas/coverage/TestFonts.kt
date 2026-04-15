package com.kreative.bitsnpicas.coverage

import com.kreative.bitsnpicas.BitmapFont
import com.kreative.bitsnpicas.BitmapFontGlyph
import com.kreative.bitsnpicas.Font

/**
 * Helpers for building small deterministic BitmapFont instances used as
 * inputs to exporter tests and as expected payloads for round-trip tests.
 */
internal object TestFonts {
    /**
     * A 5-pixel-tall box glyph starting at offset 0, advance 4.
     * Encodes as [y][x] with 0xFF for on-pixels and 0 for off-pixels.
     */
    fun solidBox(width: Int = 4, height: Int = 5, advance: Int = width): BitmapFontGlyph {
        val data = Array(height) { ByteArray(width) { 0xFF.toByte() } }
        val g = BitmapFontGlyph(data, 0, advance, height)
        return g
    }

    /** A glyph whose pixels are half the byte array filled. */
    fun halfBox(): BitmapFontGlyph {
        val data = Array(4) { y ->
            ByteArray(4) { x -> if (((x + y) and 1) == 0) 0xFF.toByte() else 0 }
        }
        return BitmapFontGlyph(data, 0, 4, 4)
    }

    /** Build a small BitmapFont with a couple of ASCII glyphs and standard metrics. */
    fun tinyFont(): BitmapFont {
        val bm = BitmapFont(
            /* emAscent     */ 6,
            /* emDescent    */ 2,
            /* lineAscent   */ 6,
            /* lineDescent  */ 2,
            /* xHeight      */ 4,
            /* capHeight    */ 6,
            /* lineGap      */ 0,
            /* newGlyphWdth */ 4
        )
        bm.setName(Font.NAME_FAMILY, "Tiny")
        bm.setName(Font.NAME_STYLE, "Regular")
        bm.setName(Font.NAME_COPYRIGHT, "Public Domain")
        bm.setName(Font.NAME_VERSION, "1.0")
        bm.setName(Font.NAME_MANUFACTURER, "BitsNPicas")
        bm.setName(Font.NAME_POSTSCRIPT, "Tiny-Regular")
        // Glyphs 'A' and 'B' and '.notdef'.
        bm.putCharacter('A'.code, solidBox())
        bm.putCharacter('B'.code, halfBox())
        bm.putCharacter(' '.code, BitmapFontGlyph(Array(0) { ByteArray(0) }, 0, 2, 0))
        bm.putNamedGlyph(".notdef", solidBox())
        return bm
    }
}
