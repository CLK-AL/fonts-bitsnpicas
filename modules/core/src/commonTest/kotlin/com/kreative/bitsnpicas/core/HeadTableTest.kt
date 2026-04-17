package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.truetype.HeadTable
import kotlin.test.Test
import kotlin.test.assertEquals

class HeadTableTest {

    @Test
    fun roundTripDefaultValues() {
        val head = HeadTable()
        head.unitsPerEm = 1000
        head.flags = 0x000B
        head.xMin = -100
        head.yMin = -200
        head.xMax = 800
        head.yMax = 900
        head.macStyle = HeadTable.MAC_STYLE_BOLD
        head.lowestRecPPEM = 8
        head.dateCreated = 3_600_000_000L
        head.dateModified = 3_700_000_000L

        val compiled = head.compile()
        assertEquals(54, compiled.size, "head table should be exactly 54 bytes")

        val head2 = HeadTable()
        head2.decompile(compiled)

        assertEquals(head.version, head2.version)
        assertEquals(head.fontRevision, head2.fontRevision)
        assertEquals(head.checkSum, head2.checkSum)
        assertEquals(head.magicNumber, head2.magicNumber)
        assertEquals(head.flags, head2.flags)
        assertEquals(head.unitsPerEm, head2.unitsPerEm)
        assertEquals(head.dateCreated, head2.dateCreated)
        assertEquals(head.dateModified, head2.dateModified)
        assertEquals(head.xMin, head2.xMin)
        assertEquals(head.yMin, head2.yMin)
        assertEquals(head.xMax, head2.xMax)
        assertEquals(head.yMax, head2.yMax)
        assertEquals(head.macStyle, head2.macStyle)
        assertEquals(head.lowestRecPPEM, head2.lowestRecPPEM)
        assertEquals(head.fontDirectionHint, head2.fontDirectionHint)
        assertEquals(head.indexToLocFormat, head2.indexToLocFormat)
        assertEquals(head.glyphDataFormat, head2.glyphDataFormat)
    }

    @Test
    fun roundTripNegativeValues() {
        val head = HeadTable()
        head.xMin = -32768
        head.yMin = -1
        head.xMax = 32767
        head.yMax = 0
        head.fontDirectionHint = -2

        val compiled = head.compile()
        val head2 = HeadTable()
        head2.decompile(compiled)

        assertEquals(-32768, head2.xMin, "xMin should preserve negative value")
        assertEquals(-1, head2.yMin, "yMin should preserve -1")
        assertEquals(32767, head2.xMax, "xMax should preserve max positive")
        assertEquals(0, head2.yMax)
        assertEquals(-2, head2.fontDirectionHint, "fontDirectionHint should preserve -2")
    }

    @Test
    fun tableIdMatchesTag() {
        val head = HeadTable()
        assertEquals("head", head.tableName)
        assertEquals(0x68656164, head.tableId)
    }
}
