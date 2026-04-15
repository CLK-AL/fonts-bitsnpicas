package com.kreative.bitsnpicas.coverage

import com.kreative.bitsnpicas.BitmapFont
import com.kreative.bitsnpicas.BitmapFontExporter
import com.kreative.bitsnpicas.BitmapFontImporter
import com.kreative.bitsnpicas.exporter.*
import com.kreative.bitsnpicas.importer.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertTrue

/**
 * Drives each simple exporter -> importer pair to cover their serialization
 * and parsing branches in one sweep. Some formats accept only subsets of the
 * font, but all must at minimum accept the BitmapFont and reject-clean.
 */
class BroadRoundTripTest {

    private fun runThrough(name: String, exporter: BitmapFontExporter, importer: BitmapFontImporter?) {
        val bm = TestFonts.tinyFont()
        val bytes = exporter.exportFontToBytes(bm)
        assertTrue(bytes.isNotEmpty(), "$name exportFontToBytes is empty")

        val baos = ByteArrayOutputStream()
        exporter.exportFontToStream(bm, baos)
        assertTrue(baos.size() > 0, "$name exportFontToStream is empty")

        val tmp = File.createTempFile("broad-$name-", ".bin")
        try {
            exporter.exportFontToFile(bm, tmp)
            assertTrue(tmp.length() > 0, "$name exportFontToFile is empty")
            if (importer != null) {
                importer.importFont(tmp)
                importer.importFont(bytes)
                importer.importFont(ByteArrayInputStream(bytes))
            }
        } finally {
            tmp.delete()
        }
    }

    @Test fun fnt_round_trip_all_three_magics() {
        val bm = TestFonts.tinyFont()
        for (magic in 1..3) {
            val bytes = FNTBitmapFontExporter(magic).exportFontToBytes(bm)
            FNTBitmapFontImporter().importFont(bytes)
        }

        // Stream/file variants.
        runThrough("fnt", FNTBitmapFontExporter(), FNTBitmapFontImporter())
    }

    @Test fun sbf_round_trip()    { runThrough("sbf", SBFBitmapFontExporter(), SBFBitmapFontImporter()) }
    @Test fun fzx_round_trip()    { runThrough("fzx", FZXBitmapFontExporter(), FZXBitmapFontImporter()) }
    @Test fun u8m_round_trip()    { runThrough("u8m", U8MBitmapFontExporter(), U8MBitmapFontImporter()) }
    @Test fun fontx_round_trip()  { runThrough("fontx", FONTXBitmapFontExporter(false), FONTXBitmapFontImporter()) }
    @Test fun hmzk_round_trip()   { runThrough("hmzk", HMZKBitmapFontExporter(), HMZKBitmapFontImporter()) }
    @Test fun hrcg_round_trip()   { runThrough("hrcg", HRCGBitmapFontExporter(), HRCGBitmapFontImporter()) }
    @Test fun mgtk_round_trip()   { runThrough("mgtk", MGTKBitmapFontExporter(), MGTKBitmapFontImporter()) }
    @Test fun cybiko_round_trip() { runThrough("cybiko", CybikoBitmapFontExporter(), CybikoBitmapFontImporter()) }
    @Test fun rockbox_round_trip() {
        val bm = TestFonts.tinyFont()
        for (magic in listOf(0x52423131, 0x52423132)) {
            val bytes = RockboxBitmapFontExporter(magic).exportFontToBytes(bm)
            RockboxBitmapFontImporter().importFont(bytes)
        }
    }
    @Test fun playdate_round_trip() {
        // Playdate exporter emits text + optional PNG. Round trip via files.
        val bm = TestFonts.tinyFont()
        val exporter = PlaydateBitmapFontExporter(false)
        val bytes = exporter.exportFontToBytes(bm)
        assertTrue(bytes.isNotEmpty())
        val tmp = File.createTempFile("playdate-", ".fnt")
        try {
            exporter.exportFontToFile(bm, tmp)
            PlaydateBitmapFontImporter().importFont(tmp)
        } finally { tmp.delete() }
    }
    @Test fun amiga_descriptor_round_trip()  { runThrough("amigaDesc",
        AmigaBitmapFontExporter.DescriptorFile(false),
        AmigaBitmapFontImporter.DescriptorFile())
    }
    @Test fun geos_round_trip() {
        val bm = TestFonts.tinyFont()
        val idgen = com.kreative.bitsnpicas.IDGenerator.Sequential(1, 1, 0x7FFF)
        val sizegen = com.kreative.bitsnpicas.PointSizeGenerator.Fixed(8)
        val exporter = GEOSBitmapFontExporter(idgen, sizegen)
        val bytes = exporter.exportFontToBytes(bm)
        assertTrue(bytes.isNotEmpty())
        val baos = ByteArrayOutputStream()
        exporter.exportFontToStream(bm, baos)
        val tmp = File.createTempFile("geos-", ".geos")
        try {
            exporter.exportFontToFile(bm, tmp)
            GEOSBitmapFontImporter().importFont(tmp)
        } finally { tmp.delete() }
    }

    // Write-only exporters (no matching importer)
    @Test fun sfont_export_only() { runThrough("sfont", SFontBitmapFontExporter(), null) }
    @Test fun tos_export_only()   { runThrough("tos", TOSBitmapFontExporter(), null) }
    @Test fun rfont_export_only() { runThrough("rfont", RFontBitmapFontExporter(), null) }
    @Test fun ttf_export_only()   { runThrough("ttf", TTFBitmapFontExporter(), null) }
    @Test fun otb_export_only()   { runThrough("otb", OTBBitmapFontExporter(), null) }
}
