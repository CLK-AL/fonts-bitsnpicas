package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.BitmapFont as JavaBitmapFont
import com.kreative.bitsnpicas.BitmapFontGlyph as JavaBitmapFontGlyph
import com.kreative.bitsnpicas.Font as JavaFont
import com.kreative.bitsnpicas.exporter.BDFBitmapFontExporter
import com.kreative.bitsnpicas.exporter.FNTBitmapFontExporter
import com.kreative.bitsnpicas.exporter.HexBitmapFontExporter
import com.kreative.bitsnpicas.exporter.PSFBitmapFontExporter
import com.kreative.bitsnpicas.importer.BDFBitmapFontImporter
import com.kreative.bitsnpicas.importer.FNTBitmapFontImporter
import com.kreative.bitsnpicas.importer.HexBitmapFontImporter
import com.kreative.bitsnpicas.importer.PSFBitmapFontImporter

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

    /**
     * Import a PSF font via the frozen Java PSFBitmapFontImporter,
     * then convert the result to commonMain types for parity testing.
     */
    public fun importPsfViaJava(bytes: ByteArray): BitmapFont {
        val importer = PSFBitmapFontImporter()
        val javaFonts: Array<JavaBitmapFont> = importer.importFont(bytes)
        val jf = javaFonts[0]

        val glyphs = mutableMapOf<Int, BitmapGlyph>()
        for ((cp, jGlyph) in jf.characters(true)) {
            val kg = fromJava(jGlyph) ?: continue
            glyphs[cp] = kg
        }

        return BitmapFont(
            glyphs = glyphs,
            emAscent = jf.emAscent,
            emDescent = jf.emDescent,
            lineAscent = jf.lineAscent,
            lineDescent = jf.lineDescent,
            xHeight = jf.xHeight,
            capHeight = jf.capHeight,
            lineGap = jf.lineGap,
            newGlyphWidth = jf.newGlyphWidth,
        )
    }

    /**
     * Import an FNT font via the frozen Java FNTBitmapFontImporter,
     * then convert the result to commonMain types for parity testing.
     */
    public fun importFntViaJava(bytes: ByteArray): BitmapFont {
        val importer = FNTBitmapFontImporter()
        val javaFonts: Array<JavaBitmapFont> = importer.importFont(bytes)
        val jf = javaFonts[0]

        val glyphs = mutableMapOf<Int, BitmapGlyph>()
        for ((cp, jGlyph) in jf.characters(true)) {
            val kg = fromJava(jGlyph) ?: continue
            glyphs[cp] = kg
        }

        return BitmapFont(
            glyphs = glyphs,
            emAscent = jf.emAscent,
            emDescent = jf.emDescent,
            lineAscent = jf.lineAscent,
            lineDescent = jf.lineDescent,
            xHeight = jf.xHeight,
            capHeight = jf.capHeight,
            lineGap = jf.lineGap,
            newGlyphWidth = jf.newGlyphWidth,
        )
    }

    /**
     * Import a BDF font via the frozen Java BDFBitmapFontImporter,
     * then convert the result to commonMain types for parity testing.
     * Takes the full BDF file as a single String (identical input as the
     * commonMain `BdfImporter.read`).
     */
    public fun importBdfViaJava(text: String): BitmapFont {
        val importer = BDFBitmapFontImporter()
        val javaFonts: Array<JavaBitmapFont> = importer.importFont(
            text.toByteArray(Charsets.UTF_8)
        )
        val jf = javaFonts[0]

        val glyphs = mutableMapOf<Int, BitmapGlyph>()
        for ((cp, jGlyph) in jf.characters(true)) {
            val kg = fromJava(jGlyph) ?: continue
            glyphs[cp] = kg
        }

        return BitmapFont(
            glyphs = glyphs,
            emAscent = jf.emAscent,
            emDescent = jf.emDescent,
            lineAscent = jf.lineAscent,
            lineDescent = jf.lineDescent,
            xHeight = jf.xHeight,
            capHeight = jf.capHeight,
            lineGap = jf.lineGap,
            newGlyphWidth = jf.newGlyphWidth,
            name = jf.getName(JavaFont.NAME_FAMILY),
        )
    }

    /**
     * Export a commonMain [BitmapFont] via the frozen Java
     * `BDFBitmapFontExporter`. Used by the jvmTest parity gate to
     * compare commonMain exporter output against the legacy path.
     *
     * This adapter bridges commonMain -> Java: it reconstructs a Java
     * `BitmapFont` from the commonMain model, populating just enough
     * state for the Java exporter to run (font metrics + per-codepoint
     * glyphs). BDF output is ASCII-safe, so we decode the Java byte
     * array as UTF-8.
     */
    public fun exportBdfViaJava(font: BitmapFont): String {
        val jf = toJavaFont(font)
        val exporter = BDFBitmapFontExporter()
        val bytes = exporter.exportFontToBytes(jf)
        return String(bytes, Charsets.UTF_8)
    }

    /**
     * Import a Hex bitmap font via the frozen Java HexBitmapFontImporter,
     * then convert the result to commonMain types for parity testing.
     * Takes the full hex file as a single String.
     */
    public fun importHexViaJava(text: String): BitmapFont {
        val importer = HexBitmapFontImporter()
        val javaFonts: Array<JavaBitmapFont> = importer.importFont(
            text.toByteArray(Charsets.UTF_8)
        )
        if (javaFonts.isEmpty()) {
            // Java importer returns empty array for empty fonts
            return BitmapFont(
                glyphs = emptyMap(),
                emAscent = 7,
                emDescent = 1,
                lineAscent = 7,
                lineDescent = 1,
                xHeight = 5,
                capHeight = 7,
                lineGap = 0,
                newGlyphWidth = 8,
            )
        }
        val jf = javaFonts[0]

        val glyphs = mutableMapOf<Int, BitmapGlyph>()
        for ((cp, jGlyph) in jf.characters(true)) {
            val kg = fromJava(jGlyph) ?: continue
            glyphs[cp] = kg
        }

        return BitmapFont(
            glyphs = glyphs,
            emAscent = jf.emAscent,
            emDescent = jf.emDescent,
            lineAscent = jf.lineAscent,
            lineDescent = jf.lineDescent,
            xHeight = jf.xHeight,
            capHeight = jf.capHeight,
            lineGap = jf.lineGap,
            newGlyphWidth = jf.newGlyphWidth,
        )
    }

    /**
     * Export a commonMain [BitmapFont] via the frozen Java
     * `PSFBitmapFontExporter`. Used by the jvmTest parity gate to
     * compare commonMain exporter output against the legacy path.
     */
    public fun exportPsfViaJava(font: BitmapFont): ByteArray {
        val jf = toJavaFont(font)
        val exporter = PSFBitmapFontExporter()
        return exporter.exportFontToBytes(jf)
    }

    /**
     * Export a commonMain [BitmapFont] via the frozen Java
     * `FNTBitmapFontExporter`. Used by the jvmTest parity gate.
     */
    public fun exportFntViaJava(font: BitmapFont): ByteArray {
        val jf = toJavaFont(font)
        val exporter = FNTBitmapFontExporter()
        return exporter.exportFontToBytes(jf)
    }

    /**
     * Export a commonMain [BitmapFont] via the frozen Java
     * `HexBitmapFontExporter`. Used by the jvmTest parity gate.
     */
    public fun exportHexViaJava(font: BitmapFont): String {
        val jf = toJavaFont(font)
        val exporter = HexBitmapFontExporter()
        val bytes = exporter.exportFontToBytes(jf)
        return String(bytes, Charsets.UTF_8)
    }

    /**
     * Bridge a commonMain [BitmapFont] into a frozen Java `BitmapFont`,
     * populating the fields needed for the Java exporters.
     */
    private fun toJavaFont(font: BitmapFont): JavaBitmapFont {
        val jf = JavaBitmapFont()
        jf.setEmAscent(font.emAscent)
        jf.setEmDescent(font.emDescent)
        jf.setLineAscent(font.lineAscent)
        jf.setLineDescent(font.lineDescent)
        jf.setXHeight(font.xHeight)
        jf.setCapHeight(font.capHeight)
        if (font.name != null) {
            jf.setName(JavaFont.NAME_FAMILY, font.name)
        }
        for ((cp, kGlyph) in font.glyphs) {
            jf.putCharacter(cp, toJava(kGlyph))
        }
        return jf
    }

    /** Compose via the frozen Java code path, returning a commonMain view. */
    public fun composeViaJava(glyphs: List<BitmapGlyph?>): BitmapGlyph? {
        val javaArr: Array<JavaBitmapFontGlyph?> = Array(glyphs.size) { i ->
            glyphs[i]?.let { toJava(it) }
        }
        return fromJava(JavaBitmapFontGlyph.compose(*javaArr))
    }
}
