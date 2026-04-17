package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.truetype.PostTable
import com.kreative.bitsnpicas.core.truetype.PostTableEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class PostTableTest {

    @Test
    fun roundTripFormat1() {
        val table = PostTable()
        table.format = PostTable.FORMAT_1
        table.italicAngle = 0
        table.fixedPitch = PostTable.FIXED_PITCH_TRUE

        val compiled = table.compile()
        val table2 = PostTable()
        table2.decompile(compiled)

        assertEquals(PostTable.FORMAT_1, table2.format)
        assertEquals(0, table2.italicAngle)
        assertEquals(PostTable.FIXED_PITCH_TRUE, table2.fixedPitch)
        assertEquals(0, table2.entries.size, "Format 1 should have no entries")
    }

    @Test
    fun roundTripFormat2WithIntegers() {
        val table = PostTable()
        table.format = PostTable.FORMAT_2
        table.entries.add(PostTableEntry(0))   // .notdef
        table.entries.add(PostTableEntry(3))   // space
        table.entries.add(PostTableEntry(36))  // A

        val compiled = table.compile()
        val table2 = PostTable()
        table2.decompile(compiled)

        assertEquals(PostTable.FORMAT_2, table2.format)
        assertEquals(3, table2.entries.size)
        assertEquals(0, table2.entries[0].intValue())
        assertEquals(3, table2.entries[1].intValue())
        assertEquals(36, table2.entries[2].intValue())
    }

    @Test
    fun roundTripFormat2WithStrings() {
        val table = PostTable()
        table.format = PostTable.FORMAT_2
        table.entries.add(PostTableEntry(0))          // .notdef
        table.entries.add(PostTableEntry("myGlyph"))  // custom name
        table.entries.add(PostTableEntry("uniF000"))  // custom name

        val compiled = table.compile()
        val table2 = PostTable()
        table2.decompile(compiled)

        assertEquals(3, table2.entries.size)
        assertEquals(true, table2.entries[0].isInteger())
        assertEquals(0, table2.entries[0].intValue())
        assertEquals(true, table2.entries[1].isString())
        assertEquals("myGlyph", table2.entries[1].stringValue())
        assertEquals(true, table2.entries[2].isString())
        assertEquals("uniF000", table2.entries[2].stringValue())
    }

    @Test
    fun roundTripFormat3() {
        val table = PostTable()
        table.format = PostTable.FORMAT_3
        table.underlinePosition = -100
        table.underlineThickness = 50

        val compiled = table.compile()
        val table2 = PostTable()
        table2.decompile(compiled)

        assertEquals(PostTable.FORMAT_3, table2.format)
        assertEquals(-100, table2.underlinePosition)
        assertEquals(50, table2.underlineThickness)
        assertEquals(0, table2.entries.size, "Format 3 should have no entries")
    }

    @Test
    fun roundTripFormat2_5() {
        val table = PostTable()
        table.format = PostTable.FORMAT_2_5
        // Each entry is an index that equals i + offset
        table.entries.add(PostTableEntry(0))  // i=0, offset=0
        table.entries.add(PostTableEntry(2))  // i=1, offset=1
        table.entries.add(PostTableEntry(3))  // i=2, offset=1

        val compiled = table.compile()
        val table2 = PostTable()
        table2.decompile(compiled)

        assertEquals(PostTable.FORMAT_2_5, table2.format)
        assertEquals(3, table2.entries.size)
        assertEquals(0, table2.entries[0].intValue())
        assertEquals(2, table2.entries[1].intValue())
        assertEquals(3, table2.entries[2].intValue())
    }

    @Test
    fun tableIdMatchesTag() {
        val table = PostTable()
        assertEquals("post", table.tableName)
        assertEquals(0x706F7374, table.tableId)
    }
}
