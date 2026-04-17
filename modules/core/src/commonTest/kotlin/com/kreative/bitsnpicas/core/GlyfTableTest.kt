package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.truetype.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlyfTableTest {

    @Test
    fun roundTripSimpleGlyph() {
        // Create a simple triangle glyph
        val entry = GlyfTableEntry()
        entry.numberOfContours = 1
        entry.xMin = 0
        entry.yMin = 0
        entry.xMax = 500
        entry.yMax = 700
        entry.endPointsOfContours = intArrayOf(2)
        entry.instructions = intArrayOf()
        // 3 points: (0,0), (500,0), (250,700), all on curve
        entry.flags = intArrayOf(
            GlyfTableEntry.FLAG_ON_CURVE or GlyfTableEntry.FLAG_X_SHORT_VECTOR or GlyfTableEntry.FLAG_POSITIVE_X_SHORT_VECTOR or GlyfTableEntry.FLAG_Y_SHORT_VECTOR or GlyfTableEntry.FLAG_POSITIVE_Y_SHORT_VECTOR,
            GlyfTableEntry.FLAG_ON_CURVE or GlyfTableEntry.FLAG_THIS_X_IS_SAME or GlyfTableEntry.FLAG_THIS_Y_IS_SAME,
            GlyfTableEntry.FLAG_ON_CURVE or GlyfTableEntry.FLAG_THIS_X_IS_SAME or GlyfTableEntry.FLAG_THIS_Y_IS_SAME,
        )
        entry.xCoordinates = intArrayOf(0, 0, 0)
        entry.yCoordinates = intArrayOf(0, 0, 0)

        val glyphData = entry.compile()
        assertTrue(glyphData.isNotEmpty())

        val entry2 = GlyfTableEntry()
        entry2.decompile(glyphData)
        assertEquals(entry.numberOfContours, entry2.numberOfContours)
        assertEquals(entry.xMin, entry2.xMin)
        assertEquals(entry.yMin, entry2.yMin)
        assertEquals(entry.xMax, entry2.xMax)
        assertEquals(entry.yMax, entry2.yMax)
        assertEquals(entry.endPointsOfContours.toList(), entry2.endPointsOfContours.toList())
        assertEquals(entry.xCoordinates.toList(), entry2.xCoordinates.toList())
        assertEquals(entry.yCoordinates.toList(), entry2.yCoordinates.toList())
    }

    @Test
    fun roundTripGlyfTableWithLoca() {
        // Empty glyph (like a space) -- stored as zero-length data
        val data1 = ByteArray(0)

        // A simple 1-contour glyph with 1 point
        val entry2 = GlyfTableEntry()
        entry2.numberOfContours = 1
        entry2.xMin = 0; entry2.yMin = 0; entry2.xMax = 100; entry2.yMax = 100
        entry2.endPointsOfContours = intArrayOf(0)
        entry2.instructions = intArrayOf()
        entry2.flags = intArrayOf(
            GlyfTableEntry.FLAG_ON_CURVE or GlyfTableEntry.FLAG_X_SHORT_VECTOR or GlyfTableEntry.FLAG_POSITIVE_X_SHORT_VECTOR or GlyfTableEntry.FLAG_Y_SHORT_VECTOR or GlyfTableEntry.FLAG_POSITIVE_Y_SHORT_VECTOR,
        )
        entry2.xCoordinates = intArrayOf(50)
        entry2.yCoordinates = intArrayOf(50)
        val data2 = entry2.compile()

        val glyf = GlyfTable()
        glyf.glyphs.add(data1)
        glyf.glyphs.add(data2)

        // Build loca
        val loca = LocaTable()
        var offset = 0
        loca.offsets.add(offset)
        offset += data1.size
        loca.offsets.add(offset)
        offset += data2.size
        loca.offsets.add(offset)

        val locaDeps = mapOf(
            TrueTypeTable.tagToId("loca") to loca as TrueTypeTable,
        )

        val glyfData = glyf.compile(locaDeps)

        val glyf2 = GlyfTable()
        glyf2.decompile(glyfData, locaDeps)
        assertEquals(2, glyf2.glyphs.size)
        assertEquals(data1.toList(), glyf2.glyphs[0].toList())
        assertEquals(data2.toList(), glyf2.glyphs[1].toList())
    }

    @Test
    fun tableIdMatchesTag() {
        val t = GlyfTable()
        assertEquals("glyf", t.tableName)
        assertEquals(0x676C7966, t.tableId)
    }
}
