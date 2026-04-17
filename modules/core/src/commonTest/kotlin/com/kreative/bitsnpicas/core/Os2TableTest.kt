package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.truetype.Os2Table
import kotlin.test.Test
import kotlin.test.assertEquals

class Os2TableTest {

    @Test
    fun roundTripMaxLength() {
        val t = Os2Table()
        t.length = Os2Table.LENGTH_MAX
        t.version = 5
        t.averageCharWidth = 500
        t.weightClass = 700
        t.widthClass = 5
        t.flags = 0x0004
        t.subscriptXSize = 100
        t.subscriptYSize = 100
        t.familyClass = 8
        t.familySubClass = 1
        t.panoseFamilyType = 2
        t.panoseWeight = 6
        t.unicodeRanges[0] = 0x00000003
        t.unicodeRanges[1] = 0x10000000
        t.vendorID = 0x4B426E50
        t.fsSelection = Os2Table.FS_SELECTION_REGULAR
        t.fsFirstCharIndex = 0x20
        t.fsLastCharIndex = 0xFFFF
        t.typoAscent = 800
        t.typoDescent = -200
        t.typoLineGap = 90
        t.winAscent = 1000
        t.winDescent = 200
        t.codePages[0] = 1
        t.xHeight = 500
        t.capHeight = 700
        t.lowerOpticalPointSize = 160  // 8pt
        t.upperOpticalPointSize = 0xFFFF

        val data = t.compile()
        assertEquals(Os2Table.LENGTH_MAX, data.size, "version 5 should be 100 bytes")

        val t2 = Os2Table()
        t2.decompile(data)
        assertEquals(t.version, t2.version)
        assertEquals(t.averageCharWidth, t2.averageCharWidth)
        assertEquals(t.weightClass, t2.weightClass)
        assertEquals(t.widthClass, t2.widthClass)
        assertEquals(t.flags, t2.flags)
        assertEquals(t.familyClass, t2.familyClass)
        assertEquals(t.familySubClass, t2.familySubClass)
        assertEquals(t.panoseFamilyType, t2.panoseFamilyType)
        assertEquals(t.panoseWeight, t2.panoseWeight)
        assertEquals(t.unicodeRanges[0], t2.unicodeRanges[0])
        assertEquals(t.unicodeRanges[1], t2.unicodeRanges[1])
        assertEquals(t.vendorID, t2.vendorID)
        assertEquals(t.fsSelection, t2.fsSelection)
        assertEquals(t.fsFirstCharIndex, t2.fsFirstCharIndex)
        assertEquals(t.fsLastCharIndex, t2.fsLastCharIndex)
        assertEquals(t.typoAscent, t2.typoAscent)
        assertEquals(t.typoDescent, t2.typoDescent)
        assertEquals(t.typoLineGap, t2.typoLineGap)
        assertEquals(t.winAscent, t2.winAscent)
        assertEquals(t.winDescent, t2.winDescent)
        assertEquals(t.codePages[0], t2.codePages[0])
        assertEquals(t.xHeight, t2.xHeight)
        assertEquals(t.capHeight, t2.capHeight)
        assertEquals(t.lowerOpticalPointSize, t2.lowerOpticalPointSize)
        assertEquals(t.upperOpticalPointSize, t2.upperOpticalPointSize)
    }

    @Test
    fun roundTripShortLength68() {
        val t = Os2Table()
        t.length = Os2Table.LENGTH_68
        t.version = 0
        t.averageCharWidth = 400
        t.weightClass = 400

        val data = t.compile()
        assertEquals(Os2Table.LENGTH_68, data.size, "Apple v0 should be 68 bytes")

        val t2 = Os2Table()
        t2.decompile(data)
        assertEquals(0, t2.version)
        assertEquals(400, t2.averageCharWidth)
        assertEquals(400, t2.weightClass)
        // Fields beyond 68 bytes should be defaults
        assertEquals(0, t2.typoAscent)
        assertEquals(0, t2.typoDescent)
    }

    @Test
    fun tableIdMatchesTag() {
        val t = Os2Table()
        assertEquals("OS/2", t.tableName)
        assertEquals(0x4F532F32, t.tableId)
    }
}
