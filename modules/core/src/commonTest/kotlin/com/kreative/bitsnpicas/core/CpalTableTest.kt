package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.truetype.CpalTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CpalTableTest {

    @Test
    fun roundTripV0() {
        val cpal = CpalTable()
        cpal.version = 0
        cpal.numPaletteEntries = 3
        cpal.colorRecordIndices = intArrayOf(0, 3)
        // ARGB colors
        cpal.colorRecordsArray = intArrayOf(
            0xFF_FF_00_00u.toInt(), // red
            0xFF_00_FF_00u.toInt(), // green
            0xFF_00_00_FFu.toInt(), // blue
            0x80_FF_FF_FFu.toInt(), // semi-transparent white
            0x00_00_00_00,          // transparent black
            0xFF_AB_CD_EFu.toInt(), // arbitrary
        )

        val compiled = cpal.compile()
        val cpal2 = CpalTable()
        cpal2.decompile(compiled)

        assertEquals(0, cpal2.version)
        assertEquals(3, cpal2.numPaletteEntries)
        assertEquals(2, cpal2.colorRecordIndices.size)
        assertEquals(0, cpal2.colorRecordIndices[0])
        assertEquals(3, cpal2.colorRecordIndices[1])
        assertEquals(6, cpal2.colorRecordsArray.size)
        assertEquals(0xFF_FF_00_00u.toInt(), cpal2.colorRecordsArray[0])
        assertEquals(0xFF_00_FF_00u.toInt(), cpal2.colorRecordsArray[1])
        assertEquals(0xFF_00_00_FFu.toInt(), cpal2.colorRecordsArray[2])
        assertEquals(0x80_FF_FF_FFu.toInt(), cpal2.colorRecordsArray[3])
        assertEquals(0x00_00_00_00, cpal2.colorRecordsArray[4])
        assertEquals(0xFF_AB_CD_EFu.toInt(), cpal2.colorRecordsArray[5])
        assertNull(cpal2.paletteTypesArray)
        assertNull(cpal2.paletteLabelsArray)
        assertNull(cpal2.paletteEntryLabelsArray)
    }

    @Test
    fun roundTripV1WithExtensions() {
        val cpal = CpalTable()
        cpal.version = 1
        cpal.numPaletteEntries = 2
        cpal.colorRecordIndices = intArrayOf(0)
        cpal.colorRecordsArray = intArrayOf(0xFF_FF_00_00u.toInt(), 0xFF_00_00_FFu.toInt())
        cpal.paletteTypesArray = intArrayOf(CpalTable.USABLE_WITH_LIGHT_BACKGROUND)
        cpal.paletteLabelsArray = intArrayOf(256)
        cpal.paletteEntryLabelsArray = intArrayOf(257, 258)

        val compiled = cpal.compile()
        val cpal2 = CpalTable()
        cpal2.decompile(compiled)

        assertEquals(1, cpal2.version)
        assertEquals(2, cpal2.numPaletteEntries)
        assertNotNull(cpal2.paletteTypesArray)
        assertEquals(1, cpal2.paletteTypesArray!!.size)
        assertEquals(CpalTable.USABLE_WITH_LIGHT_BACKGROUND, cpal2.paletteTypesArray!![0])
        assertNotNull(cpal2.paletteLabelsArray)
        assertEquals(256, cpal2.paletteLabelsArray!![0])
        assertNotNull(cpal2.paletteEntryLabelsArray)
        assertEquals(257, cpal2.paletteEntryLabelsArray!![0])
        assertEquals(258, cpal2.paletteEntryLabelsArray!![1])
    }

    @Test
    fun tableIdMatchesTag() {
        val t = CpalTable()
        assertEquals("CPAL", t.tableName)
        assertEquals(0x4350414C, t.tableId)
    }
}
