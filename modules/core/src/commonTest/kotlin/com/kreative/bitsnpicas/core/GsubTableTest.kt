package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.truetype.GsubTable
import com.kreative.bitsnpicas.core.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GsubTableTest {

    @Test
    fun roundTripV10() {
        val w = ByteWriter()
        w.writeIntBE(0x00010000) // version 1.0
        w.writeU16BE(10)         // scriptListOffset
        w.writeU16BE(20)         // featureListOffset
        w.writeU16BE(30)         // lookupListOffset
        w.writeBytes(ByteArray(50))
        val data = w.toByteArray()

        val gsub = GsubTable()
        gsub.decompile(data)

        assertEquals(0x00010000, gsub.version)
        assertEquals(10, gsub.scriptListOffset)
        assertEquals(20, gsub.featureListOffset)
        assertEquals(30, gsub.lookupListOffset)
        assertEquals(0, gsub.featureVariationsOffset)

        val compiled = gsub.compile()
        assertTrue(data.contentEquals(compiled))
    }

    @Test
    fun roundTripV11() {
        val w = ByteWriter()
        w.writeIntBE(0x00010001)
        w.writeU16BE(14)
        w.writeU16BE(100)
        w.writeU16BE(200)
        w.writeIntBE(300)
        w.writeBytes(ByteArray(20))
        val data = w.toByteArray()

        val gsub = GsubTable()
        gsub.decompile(data)

        assertEquals(0x00010001, gsub.version)
        assertEquals(14, gsub.scriptListOffset)
        assertEquals(100, gsub.featureListOffset)
        assertEquals(200, gsub.lookupListOffset)
        assertEquals(300, gsub.featureVariationsOffset)
    }

    @Test
    fun tableIdMatchesTag() {
        val t = GsubTable()
        assertEquals("GSUB", t.tableName)
        assertEquals(0x47535542, t.tableId)
    }
}
