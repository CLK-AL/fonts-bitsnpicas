package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.truetype.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class CmapTableTest {

    @Test
    fun roundTripFormat4() {
        val sub = CmapSubtableFormat4()
        sub.languageID = 0
        // Map 'A'(65) -> glyph 1, 'B'(66) -> glyph 2, 'C'(67) -> glyph 3
        val e1 = CmapSubtableSequentialEntry()
        e1.startCharCode = 65
        e1.endCharCode = 67
        e1.glyphIndex = 1
        sub.entries.add(e1)
        // Sentinel segment
        val sentinel = CmapSubtableSequentialEntry()
        sentinel.startCharCode = 0xFFFF
        sentinel.endCharCode = 0xFFFF
        sentinel.glyphIndex = 1
        sub.entries.add(sentinel)

        val data = sub.compile()
        val sub2 = CmapSubtableFormat4()
        sub2.decompile(data)

        assertEquals(0, sub2.languageID)
        assertEquals(2, sub2.entries.size)
        assertEquals(1, sub2.getGlyphIndex(65))
        assertEquals(2, sub2.getGlyphIndex(66))
        assertEquals(3, sub2.getGlyphIndex(67))
        assertEquals(0, sub2.getGlyphIndex(64))
    }

    @Test
    fun roundTripFormat4WithRandomEntry() {
        val sub = CmapSubtableFormat4()
        // Map characters 32..34 via explicit glyph IDs
        val e1 = CmapSubtableRandomEntry()
        e1.startCharCode = 32
        e1.endCharCode = 34
        e1.glyphIndex = intArrayOf(10, 20, 30)
        sub.entries.add(e1)
        // Sentinel
        val sentinel = CmapSubtableSequentialEntry()
        sentinel.startCharCode = 0xFFFF
        sentinel.endCharCode = 0xFFFF
        sentinel.glyphIndex = 1
        sub.entries.add(sentinel)

        val data = sub.compile()
        val sub2 = CmapSubtableFormat4()
        sub2.decompile(data)

        assertEquals(10, sub2.getGlyphIndex(32))
        assertEquals(20, sub2.getGlyphIndex(33))
        assertEquals(30, sub2.getGlyphIndex(34))
    }

    @Test
    fun roundTripFormat12() {
        val sub = CmapSubtableFormat12()
        sub.languageID = 0
        val e1 = CmapSubtableSequentialEntry()
        e1.startCharCode = 0x41
        e1.endCharCode = 0x5A
        e1.glyphIndex = 1
        sub.entries.add(e1)
        // Supplementary plane
        val e2 = CmapSubtableSequentialEntry()
        e2.startCharCode = 0x1F600
        e2.endCharCode = 0x1F64F
        e2.glyphIndex = 100
        sub.entries.add(e2)

        val data = sub.compile()
        val sub2 = CmapSubtableFormat12()
        sub2.decompile(data)

        assertEquals(2, sub2.entries.size)
        assertEquals(1, sub2.getGlyphIndex(0x41))   // A -> glyph 1
        assertEquals(26, sub2.getGlyphIndex(0x5A))   // Z -> glyph 26
        assertEquals(100, sub2.getGlyphIndex(0x1F600)) // emoji start
    }

    @Test
    fun roundTripFormat0() {
        val sub = CmapSubtableFormat0()
        sub.languageID = 0
        for (i in 0x20..0x7E) {
            sub.glyphIndex[i] = i - 0x1F
        }

        val data = sub.compile()
        assertEquals(262, data.size, "format 0 should be 262 bytes")

        val sub2 = CmapSubtableFormat0()
        sub2.decompile(data)
        assertEquals(sub.languageID, sub2.languageID)
        for (i in 0 until 256) {
            assertEquals(sub.glyphIndex[i], sub2.glyphIndex[i], "glyph index at $i")
        }
    }

    @Test
    fun roundTripCmapTableContainer() {
        val cmap = CmapTable()
        cmap.version = 0

        val sub4 = CmapSubtableFormat4()
        val seq = CmapSubtableSequentialEntry()
        seq.startCharCode = 0x20
        seq.endCharCode = 0x7E
        seq.glyphIndex = 1
        sub4.entries.add(seq)
        val sentinel = CmapSubtableSequentialEntry()
        sentinel.startCharCode = 0xFFFF
        sentinel.endCharCode = 0xFFFF
        sentinel.glyphIndex = 1
        sub4.entries.add(sentinel)

        cmap.subtables.add(sub4)
        cmap.entries.add(CmapTableEntry(CmapTable.PLATFORM_ID_WINDOWS, 1, sub4))

        val data = cmap.compile()

        val cmap2 = CmapTable()
        cmap2.decompile(data)

        assertEquals(0, cmap2.version)
        assertEquals(1, cmap2.entries.size)
        assertEquals(CmapTable.PLATFORM_ID_WINDOWS, cmap2.entries[0].platformID)
        assertEquals(1, cmap2.entries[0].platformSpecificID)
        val sub = cmap2.entries[0].subtable
        assertNotNull(sub)
        assertIs<CmapSubtableFormat4>(sub)
        assertEquals(1, sub.getGlyphIndex(0x20))
        assertEquals(0x5F, sub.getGlyphIndex(0x7E))  // 0x7E - 0x20 + 1 = 95
    }

    @Test
    fun roundTripFormat6() {
        val sub = CmapSubtableFormat6()
        sub.languageID = 0
        sub.firstChar = 0x20
        sub.glyphIndex = intArrayOf(1, 2, 3, 4, 5)

        val data = sub.compile()
        val sub2 = CmapSubtableFormat6()
        sub2.decompile(data)

        assertEquals(0x20, sub2.firstChar)
        assertEquals(5, sub2.glyphIndex.size)
        assertEquals(1, sub2.getGlyphIndex(0x20))
        assertEquals(5, sub2.getGlyphIndex(0x24))
        assertEquals(0, sub2.getGlyphIndex(0x25))
    }

    @Test
    fun roundTripFormat10() {
        val sub = CmapSubtableFormat10()
        sub.languageID = 0
        sub.firstChar = 0x10000
        sub.glyphIndex = intArrayOf(100, 101, 102)

        val data = sub.compile()
        val sub2 = CmapSubtableFormat10()
        sub2.decompile(data)

        assertEquals(0x10000, sub2.firstChar)
        assertEquals(3, sub2.glyphIndex.size)
        assertEquals(100, sub2.getGlyphIndex(0x10000))
        assertEquals(102, sub2.getGlyphIndex(0x10002))
    }

    @Test
    fun unknownFormatRoundTrips() {
        val sub = UnknownCmapSubtable(99, byteArrayOf(0, 99, 1, 2, 3))
        val data = sub.compile()
        val sub2 = UnknownCmapSubtable(99)
        sub2.decompile(data)
        assertEquals(data.toList(), sub2.data.toList())
    }

    @Test
    fun tableIdMatchesTag() {
        val t = CmapTable()
        assertEquals("cmap", t.tableName)
        assertEquals(0x636D6170, t.tableId)
    }
}
