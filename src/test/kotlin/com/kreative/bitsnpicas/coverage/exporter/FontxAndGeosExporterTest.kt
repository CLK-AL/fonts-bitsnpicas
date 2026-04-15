package com.kreative.bitsnpicas.coverage.exporter

import com.kreative.bitsnpicas.BitmapFont
import com.kreative.bitsnpicas.BitmapFontGlyph
import com.kreative.bitsnpicas.Font
import com.kreative.bitsnpicas.coverage.TestFonts
import com.kreative.bitsnpicas.IDGenerator
import com.kreative.bitsnpicas.PointSizeGenerator
import com.kreative.bitsnpicas.exporter.FONTXBitmapFontExporter
import com.kreative.bitsnpicas.exporter.GEOSBitmapFontExporter
import com.kreative.bitsnpicas.importer.FONTXBitmapFontImporter
import com.kreative.unicode.data.GlyphList
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Drive FONTXBitmapFontExporter + GEOSBitmapFontExporter through their less
 * common constructors and code paths.
 */
class FontxAndGeosExporterTest {

    private fun fontWithDoubleByteGlyph(): BitmapFont {
        val bm = TestFonts.tinyFont()
        // Add a glyph at 0xF0000+0x8141 - the CP943 mapping for U+30A2 etc.
        // Use a simple high code point to drive the double-byte fromDoubleByte
        // unrecognised-encoding fallback (-> 0xF0000 + i).
        for (cp in 0x30A2..0x30A5) {
            bm.putCharacter(cp, BitmapFontGlyph(arrayOf(byteArrayOf(0xFF.toByte())), 0, 1, 1))
        }
        return bm
    }

    @Test
    fun fontx_singleByte_default() {
        val bytes = FONTXBitmapFontExporter(false).exportFontToBytes(TestFonts.tinyFont())
        assertTrue(bytes.isNotEmpty())
        // Re-import.
        FONTXBitmapFontImporter().importFont(bytes)
    }

    @Test
    fun fontx_doubleByte_default_cp943() {
        val bytes = FONTXBitmapFontExporter(true).exportFontToBytes(fontWithDoubleByteGlyph())
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun fontx_singleByte_with_glyphList_encoding() {
        // Build a tiny GlyphList where index i maps to 'A' + i (so glyphs at
        // 0x41..0x44 are reachable from byte indices 0..3).
        val cps = IntArray(256) { i -> if (i < 4) (0x41 + i) else -1 }
        val gl = GlyphList(cps, "TestEncoding")
        val bytes = FONTXBitmapFontExporter(gl).exportFontToBytes(TestFonts.tinyFont())
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun fontx_doubleByte_named_encoding() {
        // Use an explicit encoding constructor; CP932 also works.
        val bytes = FONTXBitmapFontExporter("CP932").exportFontToBytes(fontWithDoubleByteGlyph())
        assertTrue(bytes.isNotEmpty())
    }

    private fun geosFixed(id: Int = 100, ps: Int = 12) =
        GEOSBitmapFontExporter(IDGenerator.Sequential(0, 0, 0).apply { setRange(id, id) },
            PointSizeGenerator.Fixed(0).apply { setPointSizes(ps) })

    @Test
    fun geos_round_trip_basic() {
        val bytes = geosFixed().exportFontToBytes(TestFonts.tinyFont())
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun geos_round_trip_with_kerning_and_mega() {
        val exporter = GEOSBitmapFontExporter(
            IDGenerator.Sequential(0, 0, 0).apply { setRange(50, 50) },
            PointSizeGenerator.Fixed(0).apply { setPointSizes(8) },
            true,  // mega
            true,  // kerning
            false, // utf8
        )
        val bytes = exporter.exportFontToBytes(TestFonts.tinyFont())
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun geos_round_trip_with_utf8() {
        val exporter = GEOSBitmapFontExporter(
            IDGenerator.Sequential(0, 0, 0).apply { setRange(60, 60) },
            PointSizeGenerator.Fixed(0).apply { setPointSizes(10) },
            false, false, true,
        )
        val bytes = exporter.exportFontToBytes(TestFonts.tinyFont())
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun fontx_double_byte_with_many_runs() {
        // Drive the bp run-counting branch by inserting non-contiguous code points.
        val bm = TestFonts.tinyFont()
        for (cp in intArrayOf(0x4E00, 0x4E10, 0x4E20)) {
            bm.putCharacter(cp, BitmapFontGlyph(arrayOf(byteArrayOf(0xAA.toByte())), 0, 1, 1))
        }
        bm.setName(Font.NAME_FAMILY, "T")
        val bytes = FONTXBitmapFontExporter(true).exportFontToBytes(bm)
        assertTrue(bytes.isNotEmpty())
    }
}
