package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.truetype.NameTable
import com.kreative.bitsnpicas.core.truetype.NameTableEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class NameTableTest {

    @Test
    fun roundTripUnicodeEntries() {
        val table = NameTable()
        table.entries.add(NameTableEntry.forUnicode(NameTableEntry.NAME_ID_FONT_FAMILY, "TestFont"))
        table.entries.add(NameTableEntry.forUnicode(NameTableEntry.NAME_ID_FONT_SUBFAMILY, "Regular"))
        table.entries.add(NameTableEntry.forUnicode(NameTableEntry.NAME_ID_FULL_NAME, "TestFont Regular"))

        val compiled = table.compile()
        val table2 = NameTable()
        table2.decompile(compiled)

        assertEquals(3, table2.entries.size, "should have 3 entries")
        for (i in table.entries.indices) {
            val e1 = table.entries[i]
            val e2 = table2.entries[i]
            assertEquals(e1.platformID, e2.platformID, "platformID mismatch at $i")
            assertEquals(e1.platformSpecificID, e2.platformSpecificID, "platformSpecificID mismatch at $i")
            assertEquals(e1.languageID, e2.languageID, "languageID mismatch at $i")
            assertEquals(e1.nameID, e2.nameID, "nameID mismatch at $i")
            assertEquals(e1.nameData.toList(), e2.nameData.toList(), "nameData mismatch at $i")
        }
    }

    @Test
    fun roundTripWindowsEntries() {
        val table = NameTable()
        table.entries.add(NameTableEntry.forWindows(NameTableEntry.NAME_ID_FONT_FAMILY, "Arial"))
        table.entries.add(NameTableEntry.forWindows(NameTableEntry.NAME_ID_POSTSCRIPT_NAME, "Arial-Bold"))

        val compiled = table.compile()
        val table2 = NameTable()
        table2.decompile(compiled)

        assertEquals(2, table2.entries.size)
        assertEquals("Arial", table2.entries[0].getNameString())
        assertEquals("Arial-Bold", table2.entries[1].getNameString())
    }

    @Test
    fun roundTripMacintoshEntry() {
        val table = NameTable()
        table.entries.add(NameTableEntry.forMacintosh(NameTableEntry.NAME_ID_FONT_FAMILY, "Courier"))

        val compiled = table.compile()
        val table2 = NameTable()
        table2.decompile(compiled)

        assertEquals(1, table2.entries.size)
        assertEquals(NameTableEntry.PLATFORM_ID_MACINTOSH, table2.entries[0].platformID)
        assertEquals("Courier", table2.entries[0].getNameString())
    }

    @Test
    fun emptyTable() {
        val table = NameTable()
        val compiled = table.compile()
        val table2 = NameTable()
        table2.decompile(compiled)
        assertEquals(0, table2.entries.size)
    }

    @Test
    fun tableIdMatchesTag() {
        val table = NameTable()
        assertEquals("name", table.tableName)
        assertEquals(0x6E616D65, table.tableId)
    }
}
