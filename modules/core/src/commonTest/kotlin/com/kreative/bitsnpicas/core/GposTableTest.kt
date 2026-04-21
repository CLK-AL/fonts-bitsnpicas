package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.truetype.GposTable
import com.kreative.bitsnpicas.core.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GposTableTest {

    @Test
    fun roundTripV10() {
        // Build a minimal GPOS v1.0 table
        val w = ByteWriter()
        w.writeIntBE(0x00010000) // version 1.0
        w.writeU16BE(10)         // scriptListOffset
        w.writeU16BE(20)         // featureListOffset
        w.writeU16BE(30)         // lookupListOffset
        // Add some dummy data for the rest
        w.writeBytes(ByteArray(50))
        val data = w.toByteArray()

        val gpos = GposTable()
        gpos.decompile(data)

        assertEquals(0x00010000, gpos.version)
        assertEquals(10, gpos.scriptListOffset)
        assertEquals(20, gpos.featureListOffset)
        assertEquals(30, gpos.lookupListOffset)
        assertEquals(0, gpos.featureVariationsOffset)

        // Round-trip: compile should return raw data as-is
        val compiled = gpos.compile()
        assertTrue(data.contentEquals(compiled))
    }

    @Test
    fun roundTripV11() {
        val w = ByteWriter()
        w.writeIntBE(0x00010001) // version 1.1
        w.writeU16BE(14)         // scriptListOffset
        w.writeU16BE(100)        // featureListOffset
        w.writeU16BE(200)        // lookupListOffset
        w.writeIntBE(300)        // featureVariationsOffset
        w.writeBytes(ByteArray(20))
        val data = w.toByteArray()

        val gpos = GposTable()
        gpos.decompile(data)

        assertEquals(0x00010001, gpos.version)
        assertEquals(14, gpos.scriptListOffset)
        assertEquals(100, gpos.featureListOffset)
        assertEquals(200, gpos.lookupListOffset)
        assertEquals(300, gpos.featureVariationsOffset)
    }

    @Test
    fun tableIdMatchesTag() {
        val t = GposTable()
        assertEquals("GPOS", t.tableName)
        assertEquals(0x47504F53, t.tableId)
    }
}
