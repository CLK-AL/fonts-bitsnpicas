package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.truetype.HheaTable
import kotlin.test.Test
import kotlin.test.assertEquals

class HheaTableTest {

    @Test
    fun roundTripDefaultValues() {
        val t = HheaTable()
        t.ascent = 800
        t.descent = -200
        t.lineGap = 90
        t.advanceWidthMax = 1200
        t.minLeftSideBearing = -50
        t.minRightSideBearing = -30
        t.xMaxExtent = 1100
        t.caretSlopeRise = 1
        t.caretSlopeRun = 0
        t.numLongHorMetrics = 256

        val data = t.compile()
        assertEquals(36, data.size, "hhea table should be 36 bytes")

        val t2 = HheaTable()
        t2.decompile(data)
        assertEquals(t.version, t2.version)
        assertEquals(t.ascent, t2.ascent)
        assertEquals(t.descent, t2.descent)
        assertEquals(t.lineGap, t2.lineGap)
        assertEquals(t.advanceWidthMax, t2.advanceWidthMax)
        assertEquals(t.minLeftSideBearing, t2.minLeftSideBearing)
        assertEquals(t.minRightSideBearing, t2.minRightSideBearing)
        assertEquals(t.xMaxExtent, t2.xMaxExtent)
        assertEquals(t.caretSlopeRise, t2.caretSlopeRise)
        assertEquals(t.caretSlopeRun, t2.caretSlopeRun)
        assertEquals(t.numLongHorMetrics, t2.numLongHorMetrics)
    }

    @Test
    fun tableIdMatchesTag() {
        val t = HheaTable()
        assertEquals("hhea", t.tableName)
        assertEquals(0x68686561, t.tableId)
    }
}
