package com.kreative.bitsnpicas.coverage

import com.kreative.bitsnpicas.exporter.OTBBitmapFontExporter
import com.kreative.bitsnpicas.exporter.TTFBitmapFontExporter
import com.kreative.bitsnpicas.truetype.TrueTypeFile
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Drives the truetype package by exporting synthetic bitmap fonts through
 * TTFBitmapFontExporter / OTBBitmapFontExporter and then re-parsing them
 * via TrueTypeFile.decompile. That exercises NameTable, Os2Table, HeadTable,
 * HheaTable, HmtxTable, MaxpTable, PostTable, GlyfTable, LocaTable, CmapTable,
 * EbdtTable / EblcTable, and the sbit family.
 */
class TrueTypeRoundTripTest {

    @Test
    fun ttf_round_trip_decompiles_cleanly() {
        val bm = TestFonts.tinyFont()
        val bytes = TTFBitmapFontExporter().exportFontToBytes(bm)
        assertTrue(bytes.isNotEmpty())
        val f = TrueTypeFile()
        f.interpret = true
        f.decompile(bytes)
        assertTrue(f.isNotEmpty())

        // Basic lookups.
        val head = f.getByTableName("head")
        assertTrue(head != null)

        // Recompile round trip.
        val recompiled = f.compile()
        assertTrue(recompiled.isNotEmpty())
    }

    @Test
    fun ttf_with_extend_win_metrics() {
        val bm = TestFonts.tinyFont()
        val bytes = TTFBitmapFontExporter(true).exportFontToBytes(bm)
        TrueTypeFile().apply { interpret = true }.decompile(bytes)

        // Size variant constructors.
        TTFBitmapFontExporter(64).exportFontToBytes(bm)
        TTFBitmapFontExporter(64, 64).exportFontToBytes(bm)
        TTFBitmapFontExporter(64, true).exportFontToBytes(bm)
        TTFBitmapFontExporter(64, 64, true).exportFontToBytes(bm)
    }

    @Test
    fun otb_round_trip() {
        val bm = TestFonts.tinyFont()
        val bytes = OTBBitmapFontExporter().exportFontToBytes(bm)
        assertTrue(bytes.isNotEmpty())
        val f = TrueTypeFile()
        f.interpret = true
        f.decompile(bytes)
        assertTrue(f.isNotEmpty())

        OTBBitmapFontExporter(true).exportFontToBytes(bm)
    }

    @Test
    fun uninterpreted_decompile_produces_unknown_tables() {
        val bm = TestFonts.tinyFont()
        val bytes = TTFBitmapFontExporter().exportFontToBytes(bm)
        val f = TrueTypeFile()
        f.interpret = false
        f.decompile(bytes)
        assertTrue(f.isNotEmpty())
        f.compile()
    }
}
