package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.truetype.CblcTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CblcTableTest {

    @Test
    fun roundTripSingleStrike() {
        val cblc = CblcTable()
        cblc.version = 0x00030000

        val bs = CblcTable.BitmapSize()
        bs.numberOfIndexSubTables = 1
        bs.colorRef = 0
        bs.startGlyphIndex = 1
        bs.endGlyphIndex = 10
        bs.ppemX = 128
        bs.ppemY = 128
        bs.bitDepth = 32
        bs.flags = 1
        // Fake subtable data
        bs.rawSubtableData = ByteArray(16) { it.toByte() }
        cblc.bitmapSizes.add(bs)

        val compiled = cblc.compile()
        val cblc2 = CblcTable()
        cblc2.decompile(compiled)

        assertEquals(0x00030000, cblc2.version)
        assertEquals(1, cblc2.bitmapSizes.size)
        val bs2 = cblc2.bitmapSizes[0]
        assertEquals(1, bs2.numberOfIndexSubTables)
        assertEquals(0, bs2.colorRef)
        assertEquals(1, bs2.startGlyphIndex)
        assertEquals(10, bs2.endGlyphIndex)
        assertEquals(128, bs2.ppemX)
        assertEquals(128, bs2.ppemY)
        assertEquals(32, bs2.bitDepth)
        assertEquals(1, bs2.flags)
        assertEquals(16, bs2.rawSubtableData.size)
        assertTrue(bs.rawSubtableData.contentEquals(bs2.rawSubtableData))
    }

    @Test
    fun roundTripMultipleStrikes() {
        val cblc = CblcTable()
        cblc.version = 0x00030000

        for (ppem in listOf(18, 36, 72)) {
            val bs = CblcTable.BitmapSize()
            bs.numberOfIndexSubTables = 2
            bs.startGlyphIndex = 0
            bs.endGlyphIndex = 50
            bs.ppemX = ppem
            bs.ppemY = ppem
            bs.bitDepth = 32
            bs.flags = 1
            bs.rawSubtableData = ByteArray(24) { (it + ppem).toByte() }
            cblc.bitmapSizes.add(bs)
        }

        val compiled = cblc.compile()
        val cblc2 = CblcTable()
        cblc2.decompile(compiled)

        assertEquals(3, cblc2.bitmapSizes.size)
        assertEquals(18, cblc2.bitmapSizes[0].ppemX)
        assertEquals(36, cblc2.bitmapSizes[1].ppemX)
        assertEquals(72, cblc2.bitmapSizes[2].ppemX)

        for (i in 0 until 3) {
            assertTrue(
                cblc.bitmapSizes[i].rawSubtableData.contentEquals(
                    cblc2.bitmapSizes[i].rawSubtableData
                )
            )
        }
    }

    @Test
    fun roundTripEmpty() {
        val cblc = CblcTable()
        val compiled = cblc.compile()
        val cblc2 = CblcTable()
        cblc2.decompile(compiled)
        assertEquals(0x00030000, cblc2.version)
        assertEquals(0, cblc2.bitmapSizes.size)
    }

    @Test
    fun tableIdMatchesTag() {
        val t = CblcTable()
        assertEquals("CBLC", t.tableName)
        assertEquals(0x43424C43, t.tableId)
    }
}
