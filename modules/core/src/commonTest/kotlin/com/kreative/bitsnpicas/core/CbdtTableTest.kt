package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.truetype.CbdtTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CbdtTableTest {

    @Test
    fun roundTripRawData() {
        val cbdt = CbdtTable()
        // Fake CBDT data: version header (4 bytes) + some bitmap data
        val fakeData = ByteArray(100) { it.toByte() }
        // Set version to 3.0 in the first 4 bytes
        fakeData[0] = 0; fakeData[1] = 3; fakeData[2] = 0; fakeData[3] = 0
        cbdt.data = fakeData

        val compiled = cbdt.compile()
        assertTrue(fakeData.contentEquals(compiled))

        val cbdt2 = CbdtTable()
        cbdt2.decompile(compiled)
        assertTrue(fakeData.contentEquals(cbdt2.data))
    }

    @Test
    fun roundTripEmpty() {
        val cbdt = CbdtTable()
        val compiled = cbdt.compile()
        assertEquals(0, compiled.size)
        val cbdt2 = CbdtTable()
        cbdt2.decompile(compiled)
        assertEquals(0, cbdt2.data.size)
    }

    @Test
    fun tableIdMatchesTag() {
        val t = CbdtTable()
        assertEquals("CBDT", t.tableName)
        assertEquals(0x43424454, t.tableId)
    }
}
