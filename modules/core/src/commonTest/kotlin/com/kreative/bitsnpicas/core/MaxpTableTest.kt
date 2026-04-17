package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.truetype.MaxpTable
import kotlin.test.Test
import kotlin.test.assertEquals

class MaxpTableTest {

    @Test
    fun roundTripVersion1() {
        val t = MaxpTable()
        t.version = MaxpTable.VERSION_DEFAULT
        t.numGlyphs = 256
        t.maxPoints = 100
        t.maxContours = 10
        t.maxComponentPoints = 50
        t.maxComponentContours = 5
        t.maxZones = 2
        t.maxTwilightPoints = 0
        t.maxStorage = 1
        t.maxFunctionDefs = 2
        t.maxInstructionDefs = 0
        t.maxStackElements = 64
        t.maxSizeOfInstructions = 128
        t.maxComponentElements = 3
        t.maxComponentDepth = 2

        val data = t.compile()
        assertEquals(32, data.size, "version 1.0 maxp should be 32 bytes")

        val t2 = MaxpTable()
        t2.decompile(data)
        assertEquals(t.version, t2.version)
        assertEquals(t.numGlyphs, t2.numGlyphs)
        assertEquals(t.maxPoints, t2.maxPoints)
        assertEquals(t.maxContours, t2.maxContours)
        assertEquals(t.maxComponentPoints, t2.maxComponentPoints)
        assertEquals(t.maxComponentContours, t2.maxComponentContours)
        assertEquals(t.maxZones, t2.maxZones)
        assertEquals(t.maxStackElements, t2.maxStackElements)
        assertEquals(t.maxSizeOfInstructions, t2.maxSizeOfInstructions)
        assertEquals(t.maxComponentElements, t2.maxComponentElements)
        assertEquals(t.maxComponentDepth, t2.maxComponentDepth)
    }

    @Test
    fun roundTripVersionCff() {
        val t = MaxpTable()
        t.version = MaxpTable.VERSION_CFF
        t.numGlyphs = 42

        val data = t.compile()
        assertEquals(6, data.size, "CFF maxp should be 6 bytes")

        val t2 = MaxpTable()
        t2.decompile(data)
        assertEquals(MaxpTable.VERSION_CFF, t2.version)
        assertEquals(42, t2.numGlyphs)
    }

    @Test
    fun tableIdMatchesTag() {
        val t = MaxpTable()
        assertEquals("maxp", t.tableName)
        assertEquals(0x6D617870, t.tableId)
    }
}
