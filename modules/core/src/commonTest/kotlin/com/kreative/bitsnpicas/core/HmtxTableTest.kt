package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.truetype.*
import kotlin.test.Test
import kotlin.test.assertEquals

class HmtxTableTest {

    @Test
    fun roundTrip() {
        val hhea = HheaTable()
        hhea.numLongHorMetrics = 3

        val maxp = MaxpTable()
        maxp.numGlyphs = 5

        val hmtx = HmtxTable()
        hmtx.entries.add(HmtxTableEntry(600, 50))
        hmtx.entries.add(HmtxTableEntry(700, -10))
        hmtx.entries.add(HmtxTableEntry(500, 0))
        // Last 2 glyphs share the last advance width
        hmtx.entries.add(HmtxTableEntry(500, 20))
        hmtx.entries.add(HmtxTableEntry(500, 30))

        val deps = mapOf(
            TrueTypeTable.tagToId("hhea") to hhea as TrueTypeTable,
            TrueTypeTable.tagToId("maxp") to maxp as TrueTypeTable,
        )

        val data = hmtx.compile(deps)
        // 3 full entries (4 bytes each) + 2 LSB-only entries (2 bytes each) = 16
        assertEquals(16, data.size)

        val hmtx2 = HmtxTable()
        hmtx2.decompile(data, deps)
        assertEquals(5, hmtx2.entries.size)
        assertEquals(600, hmtx2.entries[0].advanceWidth)
        assertEquals(50, hmtx2.entries[0].leftSideBearing)
        assertEquals(700, hmtx2.entries[1].advanceWidth)
        assertEquals(-10, hmtx2.entries[1].leftSideBearing)
        assertEquals(500, hmtx2.entries[2].advanceWidth)
        assertEquals(0, hmtx2.entries[2].leftSideBearing)
        // Glyphs beyond numLongHorMetrics inherit last advance width
        assertEquals(500, hmtx2.entries[3].advanceWidth)
        assertEquals(20, hmtx2.entries[3].leftSideBearing)
        assertEquals(500, hmtx2.entries[4].advanceWidth)
        assertEquals(30, hmtx2.entries[4].leftSideBearing)
    }

    @Test
    fun tableIdMatchesTag() {
        val t = HmtxTable()
        assertEquals("hmtx", t.tableName)
        assertEquals(0x686D7478, t.tableId)
    }
}
