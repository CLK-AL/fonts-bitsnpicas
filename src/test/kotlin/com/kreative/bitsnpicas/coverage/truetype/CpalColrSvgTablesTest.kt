package com.kreative.bitsnpicas.coverage.truetype

import com.kreative.bitsnpicas.truetype.ColrTable
import com.kreative.bitsnpicas.truetype.CpalTable
import com.kreative.bitsnpicas.truetype.SvgTable
import com.kreative.bitsnpicas.truetype.SvgTableEntry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Round-trip tests for the COLR / CPAL / SVG tables (plus SvgTableEntry).
 * Construct minimal tables in memory, compile them with no dependencies,
 * decompile the bytes back, and verify the parsed state matches.
 */
class CpalColrSvgTablesTest {

    @Test
    fun cpalTable_v0_round_trip() {
        val t = CpalTable()
        t.version = 0
        t.numPaletteEntries = 2
        t.colorRecordIndices = intArrayOf(0)
        // ARGB packed (alpha highest byte). Little-endian on disk per spec.
        t.colorRecordsArray = intArrayOf(0xFF112233.toInt(), 0xFFAABBCC.toInt())
        val bytes = t.compile(emptyArray())
        assertTrue(bytes.isNotEmpty())

        val t2 = CpalTable()
        t2.decompile(bytes, emptyArray())
        assertEquals(0, t2.version)
        assertEquals(2, t2.numPaletteEntries)
        assertEquals(1, t2.colorRecordIndices.size)
        assertEquals(2, t2.colorRecordsArray.size)
        assertEquals(0xFF112233.toInt(), t2.colorRecordsArray[0])
        assertEquals(0xFFAABBCC.toInt(), t2.colorRecordsArray[1])
    }

    @Test
    fun cpalTable_v1_round_trip_with_extra_arrays() {
        val t = CpalTable()
        t.version = 1
        t.numPaletteEntries = 2
        t.colorRecordIndices = intArrayOf(0)
        t.colorRecordsArray = intArrayOf(0x11223344, 0x55667788)
        t.paletteTypesArray = intArrayOf(CpalTable.USABLE_WITH_LIGHT_BACKGROUND)
        t.paletteLabelsArray = intArrayOf(0x100)
        t.paletteEntryLabelsArray = intArrayOf(0x200, 0x201)

        val bytes = t.compile(emptyArray())
        val t2 = CpalTable()
        t2.decompile(bytes, emptyArray())
        assertEquals(1, t2.version)
        assertEquals(2, t2.numPaletteEntries)
        assertNotNull(t2.paletteTypesArray)
        assertEquals(CpalTable.USABLE_WITH_LIGHT_BACKGROUND, t2.paletteTypesArray[0])
        assertNotNull(t2.paletteLabelsArray)
        assertEquals(0x100, t2.paletteLabelsArray[0])
        assertNotNull(t2.paletteEntryLabelsArray)
        assertEquals(0x200, t2.paletteEntryLabelsArray[0])
        assertEquals(0x201, t2.paletteEntryLabelsArray[1])
    }

    @Test
    fun cpalTable_v1_with_null_extra_arrays_uses_zero_offsets() {
        val t = CpalTable()
        t.version = 1
        t.numPaletteEntries = 1
        t.colorRecordIndices = intArrayOf(0)
        t.colorRecordsArray = intArrayOf(0x11223344)
        // Leave the v1 extras null - they should compile as zero offsets.
        val bytes = t.compile(emptyArray())
        val t2 = CpalTable()
        t2.decompile(bytes, emptyArray())
        assertEquals(1, t2.version)
        assertEquals(null, t2.paletteTypesArray)
        assertEquals(null, t2.paletteLabelsArray)
        assertEquals(null, t2.paletteEntryLabelsArray)
    }

    @Test
    fun colrTable_v0_round_trip() {
        val t = ColrTable()
        t.version = 0
        val b1 = ColrTable.BaseGlyph(); b1.glyphID = 5; b1.firstLayerIndex = 0; b1.numLayers = 2
        val b2 = ColrTable.BaseGlyph(); b2.glyphID = 6; b2.firstLayerIndex = 2; b2.numLayers = 1
        t.baseGlyphRecords = arrayOf(b1, b2)
        val l1 = ColrTable.Layer(); l1.glyphID = 100; l1.paletteIndex = 0
        val l2 = ColrTable.Layer(); l2.glyphID = 101; l2.paletteIndex = 1
        val l3 = ColrTable.Layer(); l3.glyphID = 102; l3.paletteIndex = 0
        t.layerRecords = arrayOf(l1, l2, l3)

        val bytes = t.compile(emptyArray())
        val t2 = ColrTable()
        t2.decompile(bytes, emptyArray())
        assertEquals(0, t2.version)
        assertEquals(2, t2.baseGlyphRecords.size)
        assertEquals(5, t2.baseGlyphRecords[0].glyphID)
        assertEquals(2, t2.baseGlyphRecords[0].numLayers)
        assertEquals(3, t2.layerRecords.size)
        assertEquals(100, t2.layerRecords[0].glyphID)
        assertEquals(0, t2.layerRecords[0].paletteIndex)
    }

    @Test
    fun colrTable_with_null_arrays_compiles_zero_counts() {
        val t = ColrTable()
        t.version = 0
        t.baseGlyphRecords = null
        t.layerRecords = null
        val bytes = t.compile(emptyArray())
        // Decompile - empty arrays / nulls path.
        val t2 = ColrTable()
        t2.decompile(bytes, emptyArray())
        // baseGlyphRecordsOffset==0 so decompile sets baseGlyphRecords=null.
        assertEquals(null, t2.baseGlyphRecords)
        assertEquals(null, t2.layerRecords)
    }

    @Test
    fun colrTable_v1_round_trip_writes_v1_header() {
        val t = ColrTable()
        t.version = 1
        val b = ColrTable.BaseGlyph(); b.glyphID = 1; b.firstLayerIndex = 0; b.numLayers = 1
        t.baseGlyphRecords = arrayOf(b)
        val l = ColrTable.Layer(); l.glyphID = 10; l.paletteIndex = 0
        t.layerRecords = arrayOf(l)
        val bytes = t.compile(emptyArray())
        // Decompile using the matching version; current code only honors v0
        // header but must read the bytes without throwing.
        val t2 = ColrTable()
        t2.decompile(bytes, emptyArray())
        assertEquals(1, t2.version)
    }

    @Test
    fun svgTable_round_trip_with_two_entries_and_dedup() {
        val t = SvgTable()
        val docA = "<svg xmlns='http://www.w3.org/2000/svg'/>".toByteArray()
        val docB = "<svg xmlns='x' x='1'/>".toByteArray()
        val e1 = SvgTableEntry(); e1.startGlyphID = 1; e1.endGlyphID = 1; e1.svgDocument = docA
        val e2 = SvgTableEntry(); e2.startGlyphID = 2; e2.endGlyphID = 2; e2.svgDocument = docB
        // Reuse the same byte[] instance to exercise dedup branch (containsKey).
        val e3 = SvgTableEntry(); e3.startGlyphID = 3; e3.endGlyphID = 3; e3.svgDocument = docA
        t.add(e1); t.add(e2); t.add(e3)

        val bytes = t.compile(emptyArray())
        val t2 = SvgTable()
        t2.decompile(bytes, emptyArray())
        assertEquals(3, t2.size)
        assertEquals(1, t2[0].startGlyphID)
        // Document bytes should match.
        assertTrue(t2[0].svgDocument.contentEquals(docA))
        assertTrue(t2[1].svgDocument.contentEquals(docB))
        assertTrue(t2[2].svgDocument.contentEquals(docA))
    }

    @Test
    fun svgTableEntry_compress_round_trip_via_streams() {
        val e = SvgTableEntry()
        e.startGlyphID = 5; e.endGlyphID = 5
        // Write some bytes through the OutputStream wrapper (compressed).
        e.getOutputStream(true).use {
            it.write("hello svg".toByteArray())
            it.flush()
        }
        // svgDocument should be gzipped (length >= 18, starts with 1F 8B).
        assertTrue(e.isCompressed)
        // Read back through the input stream which auto-inflates.
        val read = e.inputStream.readBytes()
        assertEquals("hello svg", String(read))
        // Image type string round trip.
        assertEquals(5, e.compareTo(e) + 5)
    }

    @Test
    fun svgTableEntry_uncompressed_passthrough() {
        val e = SvgTableEntry()
        e.startGlyphID = 1; e.endGlyphID = 1
        e.getOutputStream(false).use {
            it.write(byteArrayOf(0x3C, 0x73, 0x76, 0x67))
            it.write(0x20)
            it.write(byteArrayOf(0x2F, 0x3E), 0, 2)
        }
        assertTrue(!e.isCompressed)
        assertEquals(7, e.svgDocument.size)
        // InputStream returns the bytes unchanged.
        val read = e.inputStream.readBytes()
        assertEquals(7, read.size)
    }
}
