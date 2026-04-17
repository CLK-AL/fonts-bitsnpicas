package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.truetype.*
import kotlin.test.Test
import kotlin.test.assertEquals

class LocaTableTest {

    @Test
    fun roundTripShortFormat() {
        val head = HeadTable()
        head.indexToLocFormat = HeadTable.INDEX_TO_LOC_FORMAT_SHORT

        val maxp = MaxpTable()
        maxp.numGlyphs = 3

        val loca = LocaTable()
        loca.offsets.addAll(listOf(0, 100, 200, 300))  // numGlyphs + 1 entries

        val deps = mapOf(
            TrueTypeTable.tagToId("head") to head as TrueTypeTable,
            TrueTypeTable.tagToId("maxp") to maxp as TrueTypeTable,
        )

        val data = loca.compile(deps)
        assertEquals(8, data.size, "4 entries * 2 bytes (short format)")

        val loca2 = LocaTable()
        loca2.decompile(data, deps)
        assertEquals(listOf(0, 100, 200, 300), loca2.offsets)
    }

    @Test
    fun roundTripLongFormat() {
        val head = HeadTable()
        head.indexToLocFormat = HeadTable.INDEX_TO_LOC_FORMAT_LONG

        val maxp = MaxpTable()
        maxp.numGlyphs = 2

        val loca = LocaTable()
        loca.offsets.addAll(listOf(0, 70000, 140000))

        val deps = mapOf(
            TrueTypeTable.tagToId("head") to head as TrueTypeTable,
            TrueTypeTable.tagToId("maxp") to maxp as TrueTypeTable,
        )

        val data = loca.compile(deps)
        assertEquals(12, data.size, "3 entries * 4 bytes (long format)")

        val loca2 = LocaTable()
        loca2.decompile(data, deps)
        assertEquals(listOf(0, 70000, 140000), loca2.offsets)
    }

    @Test
    fun tableIdMatchesTag() {
        val t = LocaTable()
        assertEquals("loca", t.tableName)
        assertEquals(0x6C6F6361, t.tableId)
    }
}
