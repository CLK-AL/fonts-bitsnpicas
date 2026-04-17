package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.truetype.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * JVM parity tests for the batch 2 TrueType tables:
 * OS/2, hhea, hmtx, maxp, loca, glyf, cmap.
 *
 * Tests compile via Kotlin -> decompile via Java (and vice versa)
 * to verify byte-level compatibility with the frozen Java implementation.
 */
class TrueTypeTablesJvmParityTest {

    // ---- OS/2 ----

    @Test
    fun `os2 compile Kotlin decompile Java parity`() {
        val file = TrueTypeFile()
        val head = HeadTable(); head.unitsPerEm = 1000
        file.tables.add(head)

        val os2 = Os2Table()
        os2.version = 5
        os2.length = Os2Table.LENGTH_MAX
        os2.averageCharWidth = 500
        os2.weightClass = 700
        os2.fsSelection = Os2Table.FS_SELECTION_BOLD
        os2.typoAscent = 800
        os2.typoDescent = -200
        os2.capHeight = 700
        os2.xHeight = 500
        file.tables.add(os2)

        val kotlinBytes = file.compile()
        val javaFile = JavaLegacyAdapter.decompileTtfViaJava(kotlinBytes)
        val javaOs2 = javaFile.tables.filterIsInstance<Os2Table>().firstOrNull()
        assertNotNull(javaOs2)
        assertEquals(5, javaOs2.version)
        assertEquals(500, javaOs2.averageCharWidth)
        assertEquals(700, javaOs2.weightClass)
        assertEquals(800, javaOs2.typoAscent)
        assertEquals(-200, javaOs2.typoDescent)
        assertEquals(700, javaOs2.capHeight)
        assertEquals(500, javaOs2.xHeight)
    }

    @Test
    fun `os2 compile Java decompile Kotlin parity`() {
        val file = TrueTypeFile()
        val head = HeadTable(); head.unitsPerEm = 1000
        file.tables.add(head)

        val os2 = Os2Table()
        os2.version = 4
        os2.length = Os2Table.LENGTH_96
        os2.averageCharWidth = 600
        os2.weightClass = 400
        os2.typoAscent = 900
        file.tables.add(os2)

        val javaBytes = JavaLegacyAdapter.compileTtfViaJava(file)
        val kotlinFile = TrueTypeFile()
        kotlinFile.decompile(javaBytes)

        val kOs2 = kotlinFile.getByTableName("OS/2")
        assertNotNull(kOs2)
        assertIs<Os2Table>(kOs2)
        assertEquals(4, kOs2.version)
        assertEquals(600, kOs2.averageCharWidth)
        assertEquals(400, kOs2.weightClass)
        assertEquals(900, kOs2.typoAscent)
    }

    // ---- hhea ----

    @Test
    fun `hhea compile Kotlin decompile Java parity`() {
        val file = TrueTypeFile()
        val head = HeadTable(); head.unitsPerEm = 1000
        file.tables.add(head)

        val hhea = HheaTable()
        hhea.ascent = 800
        hhea.descent = -200
        hhea.lineGap = 90
        hhea.advanceWidthMax = 1200
        hhea.numLongHorMetrics = 256
        file.tables.add(hhea)

        val kotlinBytes = file.compile()
        val javaFile = JavaLegacyAdapter.decompileTtfViaJava(kotlinBytes)
        val javaHhea = javaFile.tables.filterIsInstance<HheaTable>().firstOrNull()
        assertNotNull(javaHhea)
        assertEquals(800, javaHhea.ascent)
        assertEquals(-200, javaHhea.descent)
        assertEquals(90, javaHhea.lineGap)
        assertEquals(1200, javaHhea.advanceWidthMax)
        assertEquals(256, javaHhea.numLongHorMetrics)
    }

    @Test
    fun `hhea compile Java decompile Kotlin parity`() {
        val file = TrueTypeFile()
        val head = HeadTable(); head.unitsPerEm = 1000
        file.tables.add(head)

        val hhea = HheaTable()
        hhea.ascent = 750
        hhea.descent = -250
        hhea.numLongHorMetrics = 100
        file.tables.add(hhea)

        val javaBytes = JavaLegacyAdapter.compileTtfViaJava(file)
        val kotlinFile = TrueTypeFile()
        kotlinFile.decompile(javaBytes)

        val kHhea = kotlinFile.getByTableName("hhea")
        assertNotNull(kHhea)
        assertIs<HheaTable>(kHhea)
        assertEquals(750, kHhea.ascent)
        assertEquals(-250, kHhea.descent)
        assertEquals(100, kHhea.numLongHorMetrics)
    }

    // ---- maxp ----

    @Test
    fun `maxp compile Kotlin decompile Java parity`() {
        val file = TrueTypeFile()
        val head = HeadTable(); head.unitsPerEm = 1000
        file.tables.add(head)

        val maxp = MaxpTable()
        maxp.version = MaxpTable.VERSION_DEFAULT
        maxp.numGlyphs = 256
        maxp.maxPoints = 100
        maxp.maxContours = 10
        file.tables.add(maxp)

        val kotlinBytes = file.compile()
        val javaFile = JavaLegacyAdapter.decompileTtfViaJava(kotlinBytes)
        val javaMaxp = javaFile.tables.filterIsInstance<MaxpTable>().firstOrNull()
        assertNotNull(javaMaxp)
        assertEquals(MaxpTable.VERSION_DEFAULT, javaMaxp.version)
        assertEquals(256, javaMaxp.numGlyphs)
        assertEquals(100, javaMaxp.maxPoints)
        assertEquals(10, javaMaxp.maxContours)
    }

    @Test
    fun `maxp compile Java decompile Kotlin parity`() {
        val file = TrueTypeFile()
        val head = HeadTable(); head.unitsPerEm = 1000
        file.tables.add(head)

        val maxp = MaxpTable()
        maxp.numGlyphs = 42
        maxp.maxPoints = 50
        file.tables.add(maxp)

        val javaBytes = JavaLegacyAdapter.compileTtfViaJava(file)
        val kotlinFile = TrueTypeFile()
        kotlinFile.decompile(javaBytes)

        val kMaxp = kotlinFile.getByTableName("maxp")
        assertNotNull(kMaxp)
        assertIs<MaxpTable>(kMaxp)
        assertEquals(42, kMaxp.numGlyphs)
        assertEquals(50, kMaxp.maxPoints)
    }

    // ---- Full TTF round-trip with dependent tables ----

    /**
     * Build a minimal but complete TTF with head, hhea, maxp, hmtx, loca, glyf.
     * Compile via Kotlin, decompile via Java, and verify structural parity.
     */
    @Test
    fun `full ttf with dependent tables - Kotlin to Java parity`() {
        val file = buildMinimalGlyphTtf()
        val kotlinBytes = file.compile()

        val javaFile = JavaLegacyAdapter.decompileTtfViaJava(kotlinBytes)

        // Verify maxp
        val jMaxp = javaFile.tables.filterIsInstance<MaxpTable>().firstOrNull()
        assertNotNull(jMaxp)
        assertEquals(2, jMaxp.numGlyphs)

        // Verify hhea
        val jHhea = javaFile.tables.filterIsInstance<HheaTable>().firstOrNull()
        assertNotNull(jHhea)
        assertEquals(2, jHhea.numLongHorMetrics)

        // Verify hmtx
        val jHmtx = javaFile.tables.filterIsInstance<HmtxTable>().firstOrNull()
        assertNotNull(jHmtx)
        assertEquals(2, jHmtx.entries.size)
        assertEquals(600, jHmtx.entries[0].advanceWidth)
        assertEquals(500, jHmtx.entries[1].advanceWidth)
    }

    @Test
    fun `full ttf with dependent tables - Java to Kotlin parity`() {
        val file = buildMinimalGlyphTtf()
        val javaBytes = JavaLegacyAdapter.compileTtfViaJava(file)

        val kotlinFile = TrueTypeFile()
        kotlinFile.decompile(javaBytes)

        val kMaxp = kotlinFile.getByTableName("maxp") as MaxpTable
        assertEquals(2, kMaxp.numGlyphs)

        val kHhea = kotlinFile.getByTableName("hhea") as HheaTable
        assertEquals(2, kHhea.numLongHorMetrics)

        val kHmtx = kotlinFile.getByTableName("hmtx") as HmtxTable
        assertEquals(2, kHmtx.entries.size)
        assertEquals(600, kHmtx.entries[0].advanceWidth)
        assertEquals(500, kHmtx.entries[1].advanceWidth)
    }

    private fun buildMinimalGlyphTtf(): TrueTypeFile {
        val file = TrueTypeFile()

        val head = HeadTable()
        head.unitsPerEm = 1000
        head.indexToLocFormat = HeadTable.INDEX_TO_LOC_FORMAT_SHORT
        file.tables.add(head)

        val maxp = MaxpTable()
        maxp.numGlyphs = 2
        file.tables.add(maxp)

        val hhea = HheaTable()
        hhea.ascent = 800
        hhea.descent = -200
        hhea.numLongHorMetrics = 2
        file.tables.add(hhea)

        val hmtx = HmtxTable()
        hmtx.entries.add(HmtxTableEntry(600, 0))
        hmtx.entries.add(HmtxTableEntry(500, 50))
        file.tables.add(hmtx)

        // Empty glyphs (like .notdef and space)
        val loca = LocaTable()
        loca.offsets.addAll(listOf(0, 0, 0))
        file.tables.add(loca)

        val glyf = GlyfTable()
        glyf.glyphs.add(ByteArray(0))
        glyf.glyphs.add(ByteArray(0))
        file.tables.add(glyf)

        return file
    }
}
