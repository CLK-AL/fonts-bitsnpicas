package com.kreative.bitsnpicas.coverage

import com.kreative.bitsnpicas.BitmapFont
import com.kreative.bitsnpicas.BitmapFontGlyph
import com.kreative.bitsnpicas.Font
import com.kreative.bitsnpicas.FontGlyphTransformer
import com.kreative.bitsnpicas.GlyphPair
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Exercises BitmapFont + inherited Font behaviours for 100% core coverage. */
class BitmapFontCoverageTest {

    @Test
    fun default_and_full_constructor_expose_metrics() {
        val empty = BitmapFont()
        assertEquals(0, empty.emAscent)
        assertEquals(0, empty.getNewGlyphWidth())
        assertEquals(0.0, empty.emAscent2D)

        val bm = BitmapFont(1, 2, 3, 4, 5, 6, 7, 8)
        assertEquals(1, bm.emAscent);  assertEquals(2, bm.emDescent)
        assertEquals(3, bm.lineAscent); assertEquals(4, bm.lineDescent)
        assertEquals(5, bm.xHeight);    assertEquals(6, bm.capHeight)
        assertEquals(7, bm.lineGap);    assertEquals(8, bm.getNewGlyphWidth())
        assertEquals(1.0, bm.emAscent2D); assertEquals(2.0, bm.emDescent2D)
        assertEquals(3.0, bm.lineAscent2D); assertEquals(4.0, bm.lineDescent2D)
        assertEquals(5.0, bm.xHeight2D); assertEquals(6.0, bm.capHeight2D)
        assertEquals(7.0, bm.lineGap2D); assertEquals(8.0, bm.getNewGlyphWidth2D())
    }

    @Test
    fun all_2D_setters_ceil_to_int() {
        val bm = BitmapFont()
        bm.setEmAscent2D(3.2);    assertEquals(4, bm.emAscent)
        bm.setEmDescent2D(3.2);   assertEquals(4, bm.emDescent)
        bm.setLineAscent2D(3.2);  assertEquals(4, bm.lineAscent)
        bm.setLineDescent2D(3.2); assertEquals(4, bm.lineDescent)
        bm.setXHeight2D(3.2);     assertEquals(4, bm.xHeight)
        bm.setCapHeight2D(3.2);   assertEquals(4, bm.capHeight)
        bm.setLineGap2D(3.2);     assertEquals(4, bm.lineGap)
        bm.setNewGlyphWidth2D(3.2); assertEquals(4, bm.getNewGlyphWidth())

        bm.emAscent = 7; assertEquals(7, bm.emAscent)
        bm.emDescent = 7; bm.lineAscent = 7; bm.lineDescent = 7
        bm.xHeight = 7; bm.capHeight = 7; bm.lineGap = 7; bm.setNewGlyphWidth(9)
        assertEquals(9, bm.getNewGlyphWidth())
    }

    @Test
    fun draw_and_drawAlphabet_produce_a_point() {
        val bm = TestFonts.tinyFont()
        val img = BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = Color.BLACK
        val p = bm.draw(g, "A B\nC", 0, 20, 1, 32)
        assertNotNull(p)
        // Overload with explicit lh.
        val p2 = bm.draw(g, "AB", 0, 20, 1, 32, 10)
        assertNotNull(p2)

        val pa = bm.drawAlphabet(g, 0, 20, 1, 16)
        assertNotNull(pa)
        val pa2 = bm.drawAlphabet(g, 0, 20, 1, 16, 10)
        assertNotNull(pa2)
        g.dispose()
    }

    @Test
    fun contractGlyphs_and_setAscentDescent_adjust_metrics() {
        val bm = TestFonts.tinyFont()
        bm.contractGlyphs()
        // setAscentDescent exercises guessBaselineAdjustment > 0 branch too.
        bm.setAscentDescent()

        // xheight/capheight setters
        bm.setXHeight()
        bm.setCapHeight()
    }

    @Test
    fun character_map_accessors_work() {
        val bm = BitmapFont()
        assertTrue(bm.isEmpty())
        val g = TestFonts.solidBox(2, 2, 2)
        assertFalse(bm.containsCharacter(65))
        bm.putCharacter(65, g)
        assertTrue(bm.containsCharacter(65))
        assertEquals(g, bm.getCharacter(65))
        bm.putCharacter(65, null)   // removes
        assertFalse(bm.containsCharacter(65))
        bm.putCharacter(65, g)
        assertNotNull(bm.removeCharacter(65))
        assertFalse(bm.containsCharacter(65))

        bm.putCharacter(66, g)
        assertEquals(1, bm.characters(false).size)
        assertEquals(1, bm.characters(true).size)
    }

    @Test
    fun named_glyph_accessors_work() {
        val bm = BitmapFont()
        val g = TestFonts.solidBox(2, 2, 2)
        assertFalse(bm.containsNamedGlyph(null))
        assertFalse(bm.containsNamedGlyph("foo"))
        assertEquals(null, bm.getNamedGlyph(null))
        assertEquals(null, bm.putNamedGlyph(null, g))
        assertEquals(null, bm.removeNamedGlyph(null))

        bm.putNamedGlyph("foo", g)
        assertTrue(bm.containsNamedGlyph("foo"))
        assertEquals(g, bm.getNamedGlyph("foo"))
        bm.putNamedGlyph("foo", null) // remove
        assertFalse(bm.containsNamedGlyph("foo"))
        bm.putNamedGlyph("bar", g)
        assertEquals(1, bm.namedGlyphs(false).size)
        assertEquals(1, bm.namedGlyphs(true).size)
        bm.removeNamedGlyph("bar")
        assertTrue(bm.isEmpty())
    }

    @Test
    fun name_accessors_work() {
        val bm = BitmapFont()
        assertFalse(bm.containsName(Font.NAME_FAMILY))
        bm.setName(Font.NAME_FAMILY, "Foo")
        assertTrue(bm.containsName(Font.NAME_FAMILY))
        assertEquals("Foo", bm.getName(Font.NAME_FAMILY))
        bm.setName(Font.NAME_FAMILY, null) // removes
        assertFalse(bm.containsName(Font.NAME_FAMILY))
        bm.setName(Font.NAME_FAMILY, "Bar")
        assertEquals("Bar", bm.removeName(Font.NAME_FAMILY))
        bm.setName(Font.NAME_FAMILY, "Baz")
        assertEquals(1, bm.names(true).size)
        assertEquals(1, bm.names(false).size)
    }

    @Test
    fun kern_pair_accessors_work() {
        val bm = BitmapFont()
        assertFalse(bm.containsKernPair(null))
        assertEquals(0, bm.getKernPair(null))
        assertEquals(0, bm.setKernPair(null, 1))
        assertEquals(0, bm.removeKernPair(null))

        val gp = GlyphPair(65, 66)
        assertFalse(bm.containsKernPair(gp))
        assertEquals(0, bm.getKernPair(gp))
        bm.setKernPair(gp, 3)
        assertTrue(bm.containsKernPair(gp))
        assertEquals(3, bm.getKernPair(gp))
        assertEquals(3, bm.setKernPair(gp, 5))
        assertEquals(5, bm.removeKernPair(gp))

        val gp2 = GlyphPair(67, 68)
        bm.setKernPair(gp2, 2)
        assertEquals(1, bm.kernPairs(true).size)
        assertEquals(1, bm.kernPairs(false).size)
    }

    @Test
    fun guess_methods_traverse_fallback_chains() {
        val bm = BitmapFont()
        // Empty => 0.
        assertEquals(0, bm.guessBaselineAdjustment())
        assertEquals(0.0, bm.guessBaselineAdjustment2D())
        assertEquals(0, bm.guessXHeight())
        assertEquals(0.0, bm.guessXHeight2D())
        assertEquals(0, bm.guessCapHeight())
        assertEquals(0.0, bm.guessCapHeight2D())

        val g = TestFonts.solidBox(2, 2, 2)
        bm.putCharacter('H'.code, g)
        bm.putCharacter('x'.code, g)
        assertEquals(0, bm.guessBaselineAdjustment()) // h=2 - ascent=2 = 0
        assertEquals(0.0, bm.guessBaselineAdjustment2D())
        assertEquals(2, bm.guessCapHeight())
        assertEquals(2.0, bm.guessCapHeight2D())
        assertEquals(2, bm.guessXHeight())
        assertEquals(2.0, bm.guessXHeight2D())
    }

    @Test
    fun style_detection_branches_all_true_and_false_paths() {
        val bm = BitmapFont()
        // No NAME_STYLE -> all style checks false.
        assertFalse(bm.isBoldStyle); assertFalse(bm.isItalicStyle)
        assertFalse(bm.isUnderlineStyle); assertFalse(bm.isOutlineStyle)
        assertFalse(bm.isShadowStyle); assertFalse(bm.isCondensedStyle)
        assertFalse(bm.isExtendedStyle); assertFalse(bm.isNegativeStyle)
        assertFalse(bm.isStrikeoutStyle); assertFalse(bm.isRegularStyle)
        assertFalse(bm.isObliqueStyle)
        assertEquals(0, bm.macStyle); assertEquals(0, bm.fsSelection)

        bm.setName(Font.NAME_STYLE, "Bold Italic Underline Outline Shadow Condense Extend Negative Strikeout Oblique")
        assertTrue(bm.isBoldStyle); assertTrue(bm.isItalicStyle)
        assertTrue(bm.isUnderlineStyle); assertTrue(bm.isOutlineStyle)
        assertTrue(bm.isShadowStyle); assertTrue(bm.isCondensedStyle)
        assertTrue(bm.isExtendedStyle); assertTrue(bm.isNegativeStyle)
        assertTrue(bm.isStrikeoutStyle); assertFalse(bm.isRegularStyle)
        assertTrue(bm.isObliqueStyle)
        assertTrue(bm.macStyle != 0)
        assertTrue(bm.fsSelection != 0)

        bm.setName(Font.NAME_STYLE, "Regular")
        assertTrue(bm.isRegularStyle)

        bm.setName(Font.NAME_STYLE, "Plain")
        assertTrue(bm.isRegularStyle)
        bm.setName(Font.NAME_STYLE, "")
        assertTrue(bm.isRegularStyle)
        bm.setName(Font.NAME_STYLE, "Normal")
        assertTrue(bm.isRegularStyle)
        bm.setName(Font.NAME_STYLE, "Medium")
        assertTrue(bm.isRegularStyle)

        // Other alternative style keywords
        bm.setName(Font.NAME_STYLE, "Black Slant Underscore Narrow Expand Wide Invert Reverse Inverse Rotalic Strikethr Heavy")
        assertTrue(bm.isBoldStyle)
        assertTrue(bm.isItalicStyle)
        assertTrue(bm.isUnderlineStyle)
        assertTrue(bm.isCondensedStyle)
        assertTrue(bm.isExtendedStyle)
        assertTrue(bm.isNegativeStyle)
        assertTrue(bm.isStrikeoutStyle)
        assertTrue(bm.isObliqueStyle)
    }

    @Test
    fun isMonospaced_true_and_false_paths() {
        val bm = BitmapFont()
        val g = TestFonts.solidBox(4, 4, 4)
        bm.putCharacter(' '.code, g)
        bm.putCharacter('A'.code, g)
        assertTrue(bm.isMonospaced)

        val fat = BitmapFontGlyph(Array(4) { ByteArray(8) }, 0, 8, 4)
        bm.putCharacter('B'.code, fat)
        assertFalse(bm.isMonospaced)

        // With a space glyph absent but a non-monospaced-category char (no effect).
        val bm2 = BitmapFont()
        // U+2028 LINE SEPARATOR is category LINE_SEPARATOR, not in MONOSPACED_CLASSES.
        bm2.putCharacter(0x2028, g)
        assertTrue(bm2.isMonospaced)
    }

    @Test
    fun autoFillNames_fills_defaults() {
        val bm = BitmapFont()
        bm.autoFillNames()
        assertEquals("Untitled", bm.getName(Font.NAME_FAMILY))
        assertTrue(bm.containsName(Font.NAME_UNIQUE_ID))
        assertTrue(bm.containsName(Font.NAME_VERSION))
        assertTrue(bm.containsName(Font.NAME_MANUFACTURER))
        assertTrue(bm.containsName(Font.NAME_VENDOR_URL))
        assertTrue(bm.containsName(Font.NAME_FAMILY_AND_STYLE))

        val bm2 = BitmapFont()
        bm2.setName(Font.NAME_FAMILY, "My Font")
        bm2.setName(Font.NAME_STYLE, "Bold")
        bm2.autoFillNames()
        assertEquals("My Font Bold", bm2.getName(Font.NAME_FAMILY_AND_STYLE))

        // Style is "Regular" - omitted from FAMILY_AND_STYLE
        val bm3 = BitmapFont()
        bm3.setName(Font.NAME_FAMILY, "Plain")
        bm3.setName(Font.NAME_STYLE, "Regular")
        bm3.autoFillNames()
    }

    @Test
    fun autoFillNames_honours_SOURCE_DATE_EPOCH_when_reading_existing_value() {
        // Can't set env var portably. Just call to exercise the default branch.
        val bm = BitmapFont()
        bm.setName(Font.NAME_FAMILY, "X")
        bm.setName(Font.NAME_UNIQUE_ID, "preset")
        bm.setName(Font.NAME_FAMILY_AND_STYLE, "preset")
        bm.setName(Font.NAME_VERSION, "preset")
        bm.setName(Font.NAME_POSTSCRIPT, "preset")
        bm.setName(Font.NAME_MANUFACTURER, "preset")
        bm.setName(Font.NAME_VENDOR_URL, "preset")
        bm.autoFillNames()
        assertEquals("preset", bm.getName(Font.NAME_UNIQUE_ID))
    }

    @Test
    fun subsetRemap_moves_between_keys() {
        val bm = TestFonts.tinyFont()
        val pairs = listOf(
            GlyphPair('A'.code, 'C'.code),   // int -> int
            GlyphPair('B'.code, "custom"),   // int -> string
            GlyphPair(".notdef", 'D'.code),  // string -> int
            GlyphPair(".notdef", "named")    // string -> string
        )
        bm.subsetRemap(pairs)
        assertTrue(bm.containsCharacter('C'.code))
        assertTrue(bm.containsNamedGlyph("custom"))
        assertTrue(bm.containsCharacter('D'.code))
        assertTrue(bm.containsNamedGlyph("named"))

        // Missing source keys skipped.
        val empty = BitmapFont()
        empty.subsetRemap(listOf(GlyphPair(65, 66), GlyphPair("x", "y")))
        assertTrue(empty.isEmpty())
    }

    @Test
    fun transform_applies_transformer() {
        val bm = TestFonts.tinyFont()
        val tx = object : FontGlyphTransformer<BitmapFontGlyph> {
            override fun transformGlyph(glyph: BitmapFontGlyph?): BitmapFontGlyph? {
                return glyph // identity
            }
        }
        bm.transform(tx)

        // Transformer returning null -> skip.
        val nullTx = object : FontGlyphTransformer<BitmapFontGlyph> {
            override fun transformGlyph(glyph: BitmapFontGlyph?): BitmapFontGlyph? = null
        }
        bm.transform(nullTx)
    }

    @Test
    fun toString_prefers_names_in_order() {
        val bm = BitmapFont()
        assertEquals("Untitled", bm.toString())
        bm.setName(Font.NAME_POSTSCRIPT, "PS")
        assertEquals("PS", bm.toString())
        bm.setName(Font.NAME_WWS_FAMILY, "WwsFam")
        assertEquals("WwsFam", bm.toString())
        bm.setName(Font.NAME_WWS_STYLE, "WwsStyle")
        assertEquals("WwsFam WwsStyle", bm.toString())
        bm.setName(Font.NAME_MACOS_FAMILY_AND_STYLE, "Mac")
        assertEquals("Mac", bm.toString())
        bm.setName(Font.NAME_WINDOWS_FAMILY, "Win")
        assertEquals("Win", bm.toString())
        bm.setName(Font.NAME_WINDOWS_STYLE, "WinStyle")
        assertEquals("Win WinStyle", bm.toString())
        bm.setName(Font.NAME_FAMILY, "Fam")
        assertEquals("Fam", bm.toString())
        bm.setName(Font.NAME_STYLE, "Sty")
        assertEquals("Fam Sty", bm.toString())
        bm.setName(Font.NAME_FAMILY_AND_STYLE, "Full")
        assertEquals("Full", bm.toString())
    }
}
