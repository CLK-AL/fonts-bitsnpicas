package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.truetype.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * JVM parity tests for COLR, CPAL, and SVG tables.
 *
 * Tests compile via Kotlin -> decompile via Java (and vice versa)
 * to verify byte-level compatibility with the frozen Java implementation.
 */
class ColourTablesJvmParityTest {

    // ---- COLR ----

    @Test
    fun `colr compile Kotlin decompile Java parity`() {
        val file = buildMinimalTtfWith(buildColrTable())
        val kotlinBytes = file.compile()
        val javaFile = JavaLegacyAdapter.decompileTtfViaJava(kotlinBytes)

        val jColr = javaFile.tables.filterIsInstance<ColrTable>().firstOrNull()
        assertNotNull(jColr)
        assertEquals(0, jColr.version)
        assertEquals(2, jColr.baseGlyphRecords.size)
        assertEquals(10, jColr.baseGlyphRecords[0].glyphID)
        assertEquals(3, jColr.layerRecords.size)
    }

    @Test
    fun `colr compile Java decompile Kotlin parity`() {
        val file = buildMinimalTtfWith(buildColrTable())
        val javaBytes = JavaLegacyAdapter.compileTtfViaJava(file)
        val kotlinFile = TrueTypeFile()
        kotlinFile.decompile(javaBytes)

        val kColr = kotlinFile.getByTableName("COLR")
        assertNotNull(kColr)
        assertIs<ColrTable>(kColr)
        assertEquals(0, kColr.version)
        assertEquals(2, kColr.baseGlyphRecords.size)
        assertEquals(10, kColr.baseGlyphRecords[0].glyphID)
        assertEquals(3, kColr.layerRecords.size)
    }

    // ---- CPAL ----

    @Test
    fun `cpal compile Kotlin decompile Java parity`() {
        val file = buildMinimalTtfWith(buildCpalTable())
        val kotlinBytes = file.compile()
        val javaFile = JavaLegacyAdapter.decompileTtfViaJava(kotlinBytes)

        val jCpal = javaFile.tables.filterIsInstance<CpalTable>().firstOrNull()
        assertNotNull(jCpal)
        assertEquals(0, jCpal.version)
        assertEquals(2, jCpal.numPaletteEntries)
        assertEquals(3, jCpal.colorRecordsArray.size)
        assertEquals(0xFF_FF_00_00u.toInt(), jCpal.colorRecordsArray[0])
    }

    @Test
    fun `cpal compile Java decompile Kotlin parity`() {
        val file = buildMinimalTtfWith(buildCpalTable())
        val javaBytes = JavaLegacyAdapter.compileTtfViaJava(file)
        val kotlinFile = TrueTypeFile()
        kotlinFile.decompile(javaBytes)

        val kCpal = kotlinFile.getByTableName("CPAL")
        assertNotNull(kCpal)
        assertIs<CpalTable>(kCpal)
        assertEquals(0, kCpal.version)
        assertEquals(2, kCpal.numPaletteEntries)
        assertEquals(3, kCpal.colorRecordsArray.size)
        assertEquals(0xFF_FF_00_00u.toInt(), kCpal.colorRecordsArray[0])
    }

    // ---- SVG ----

    @Test
    fun `svg compile Kotlin decompile Java parity`() {
        val file = buildMinimalTtfWith(buildSvgTable())
        val kotlinBytes = file.compile()
        val javaFile = JavaLegacyAdapter.decompileTtfViaJava(kotlinBytes)

        val jSvg = javaFile.tables.filterIsInstance<SvgTable>().firstOrNull()
        assertNotNull(jSvg)
        assertEquals(2, jSvg.entries.size)
        assertEquals(1, jSvg.entries[0].startGlyphID)
        assertEquals("<svg>a</svg>", jSvg.entries[0].svgDocument.decodeToString())
    }

    @Test
    fun `svg compile Java decompile Kotlin parity`() {
        val file = buildMinimalTtfWith(buildSvgTable())
        val javaBytes = JavaLegacyAdapter.compileTtfViaJava(file)
        val kotlinFile = TrueTypeFile()
        kotlinFile.decompile(javaBytes)

        val kSvg = kotlinFile.getByTableName("SVG ")
        assertNotNull(kSvg)
        assertIs<SvgTable>(kSvg)
        assertEquals(2, kSvg.entries.size)
        assertEquals(1, kSvg.entries[0].startGlyphID)
        assertEquals("<svg>a</svg>", kSvg.entries[0].svgDocument.decodeToString())
    }

    // ---- Helpers ----

    private fun buildColrTable(): ColrTable {
        val colr = ColrTable()
        colr.version = 0
        colr.baseGlyphRecords.add(ColrTable.BaseGlyph().apply {
            glyphID = 10; firstLayerIndex = 0; numLayers = 2
        })
        colr.baseGlyphRecords.add(ColrTable.BaseGlyph().apply {
            glyphID = 20; firstLayerIndex = 2; numLayers = 1
        })
        colr.layerRecords.add(ColrTable.Layer().apply { glyphID = 100; paletteIndex = 0 })
        colr.layerRecords.add(ColrTable.Layer().apply { glyphID = 101; paletteIndex = 1 })
        colr.layerRecords.add(ColrTable.Layer().apply { glyphID = 102; paletteIndex = 2 })
        return colr
    }

    private fun buildCpalTable(): CpalTable {
        val cpal = CpalTable()
        cpal.version = 0
        cpal.numPaletteEntries = 2
        cpal.colorRecordIndices = intArrayOf(0)
        cpal.colorRecordsArray = intArrayOf(
            0xFF_FF_00_00u.toInt(),
            0xFF_00_FF_00u.toInt(),
            0xFF_00_00_FFu.toInt(),
        )
        return cpal
    }

    private fun buildSvgTable(): SvgTable {
        val svg = SvgTable()
        svg.entries.add(SvgTableEntry().apply {
            startGlyphID = 1; endGlyphID = 1
            svgDocument = "<svg>a</svg>".encodeToByteArray()
        })
        svg.entries.add(SvgTableEntry().apply {
            startGlyphID = 5; endGlyphID = 8
            svgDocument = "<svg>b</svg>".encodeToByteArray()
        })
        return svg
    }

    private fun buildMinimalTtfWith(table: TrueTypeTable): TrueTypeFile {
        val file = TrueTypeFile()
        val head = HeadTable()
        head.unitsPerEm = 1000
        file.tables.add(head)
        file.tables.add(table)
        return file
    }
}
