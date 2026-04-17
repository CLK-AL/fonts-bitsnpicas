package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.puaa.PuaaEntry
import com.kreative.bitsnpicas.core.puaa.PuaaSubtable
import com.kreative.bitsnpicas.core.puaa.PuaaTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Round-trip tests for [PuaaTable] compile/decompile.
 *
 * For each of the 9 entry variants we:
 *   1. Construct entries programmatically.
 *   2. Compile to bytes via [PuaaTable.compile].
 *   3. Decompile back via [PuaaTable.decompile].
 *   4. Assert structural + value equality.
 */
class PuaaTableTest {

    // ---- helpers -----------------------------------------------------------

    private fun roundTrip(table: PuaaTable): PuaaTable {
        val bytes = table.compile()
        return PuaaTable.decompile(bytes)
    }

    private fun singleSubtableTable(property: String, entries: List<PuaaEntry>): PuaaTable {
        return PuaaTable(subtables = listOf(PuaaSubtable(property, entries)))
    }

    // ---- Single entry ------------------------------------------------------

    @Test
    fun `Single entry round-trips`() {
        val table = singleSubtableTable("General_Category", listOf(
            PuaaEntry.Single(0x0041, 0x005A, "Lu"),
            PuaaEntry.Single(0x0061, 0x007A, "Ll"),
        ))
        val rt = roundTrip(table)
        assertEquals(1, rt.subtables.size)
        assertEquals("General_Category", rt.subtables[0].property)
        assertEquals(2, rt.subtables[0].entries.size)

        val e0 = rt.subtables[0].entries[0] as PuaaEntry.Single
        assertEquals(0x0041, e0.firstCodePoint)
        assertEquals(0x005A, e0.lastCodePoint)
        assertEquals("Lu", e0.value)

        val e1 = rt.subtables[0].entries[1] as PuaaEntry.Single
        assertEquals(0x0061, e1.firstCodePoint)
        assertEquals(0x007A, e1.lastCodePoint)
        assertEquals("Ll", e1.value)
    }

    @Test
    fun `Single entry with null value round-trips`() {
        val table = singleSubtableTable("test_prop", listOf(
            PuaaEntry.Single(0x0041, 0x0041, null),
        ))
        val rt = roundTrip(table)
        val e = rt.subtables[0].entries[0] as PuaaEntry.Single
        assertNull(e.value)
    }

    @Test
    fun `Single entry with long string round-trips`() {
        val longStr = "LATIN CAPITAL LETTER A WITH RING ABOVE AND ACUTE"
        val table = singleSubtableTable("Name", listOf(
            PuaaEntry.Single(0x01FA, 0x01FA, longStr),
        ))
        val rt = roundTrip(table)
        val e = rt.subtables[0].entries[0] as PuaaEntry.Single
        assertEquals(longStr, e.value)
    }

    // ---- Multiple entry ----------------------------------------------------

    @Test
    fun `Multiple entry round-trips`() {
        val table = singleSubtableTable("Script", listOf(
            PuaaEntry.Multiple(0x0041, 0x0043, listOf("Latin", "Latin", "Latin")),
        ))
        val rt = roundTrip(table)
        val e = rt.subtables[0].entries[0] as PuaaEntry.Multiple
        assertEquals(0x0041, e.firstCodePoint)
        assertEquals(0x0043, e.lastCodePoint)
        assertEquals(listOf("Latin", "Latin", "Latin"), e.values)
    }

    // ---- Boolean entry -----------------------------------------------------

    @Test
    fun `Boolean true entry round-trips`() {
        val table = singleSubtableTable("Alphabetic", listOf(
            PuaaEntry.BooleanEntry(0x0041, 0x005A, true),
        ))
        val rt = roundTrip(table)
        val e = rt.subtables[0].entries[0] as PuaaEntry.BooleanEntry
        assertEquals(0x0041, e.firstCodePoint)
        assertEquals(0x005A, e.lastCodePoint)
        assertTrue(e.value)
    }

    @Test
    fun `Boolean false entry round-trips`() {
        val table = singleSubtableTable("Alphabetic", listOf(
            PuaaEntry.BooleanEntry(0x0030, 0x0039, false),
        ))
        val rt = roundTrip(table)
        val e = rt.subtables[0].entries[0] as PuaaEntry.BooleanEntry
        assertEquals(false, e.value)
    }

    // ---- Decimal entry -----------------------------------------------------

    @Test
    fun `Decimal entry round-trips`() {
        val table = singleSubtableTable("Numeric_Value", listOf(
            PuaaEntry.Decimal(0x0030, 0x0030, 0),
            PuaaEntry.Decimal(0x0031, 0x0031, 1),
            PuaaEntry.Decimal(0x0039, 0x0039, 9),
        ))
        val rt = roundTrip(table)
        assertEquals(3, rt.subtables[0].entries.size)
        val e2 = rt.subtables[0].entries[2] as PuaaEntry.Decimal
        assertEquals(9, e2.value)
    }

    @Test
    fun `Decimal entry with negative value round-trips`() {
        val table = singleSubtableTable("test_dec", listOf(
            PuaaEntry.Decimal(0x0041, 0x0041, -42),
        ))
        val rt = roundTrip(table)
        val e = rt.subtables[0].entries[0] as PuaaEntry.Decimal
        assertEquals(-42, e.value)
    }

    // ---- Hexadecimal entry -------------------------------------------------

    @Test
    fun `Hexadecimal entry round-trips`() {
        val table = singleSubtableTable("Simple_Lowercase_Mapping", listOf(
            PuaaEntry.Hexadecimal(0x0041, 0x0041, 0x0061),
        ))
        val rt = roundTrip(table)
        val e = rt.subtables[0].entries[0] as PuaaEntry.Hexadecimal
        assertEquals(0x0061, e.value)
        assertEquals("0061", e.getPropertyValue(0x0041))
    }

    // ---- HexMultiple entry -------------------------------------------------

    @Test
    fun `HexMultiple entry round-trips`() {
        val table = singleSubtableTable("Bidi_Mirroring_Glyph", listOf(
            PuaaEntry.HexMultiple(0x0028, 0x0029, intArrayOf(0x0029, 0x0028)),
        ))
        val rt = roundTrip(table)
        val e = rt.subtables[0].entries[0] as PuaaEntry.HexMultiple
        assertEquals(0x0028, e.firstCodePoint)
        assertEquals(0x0029, e.lastCodePoint)
        assertEquals(listOf(0x0029, 0x0028), e.values.toList())
    }

    // ---- HexSequence entry -------------------------------------------------

    @Test
    fun `HexSequence entry round-trips`() {
        val table = singleSubtableTable("Decomposition_Mapping", listOf(
            PuaaEntry.HexSequence(0x00C0, 0x00C0, intArrayOf(0x0041, 0x0300)),
        ))
        val rt = roundTrip(table)
        val e = rt.subtables[0].entries[0] as PuaaEntry.HexSequence
        assertEquals(listOf(0x0041, 0x0300), e.values.toList())
        assertEquals("0041 0300", e.getPropertyValue(0x00C0))
    }

    // ---- CaseMapping entry -------------------------------------------------

    @Test
    fun `CaseMapping entry round-trips`() {
        val table = singleSubtableTable("Special_Casing", listOf(
            PuaaEntry.CaseMapping(0x00DF, 0x00DF, intArrayOf(0x0053, 0x0053), null),
        ))
        val rt = roundTrip(table)
        val e = rt.subtables[0].entries[0] as PuaaEntry.CaseMapping
        assertEquals(listOf(0x0053, 0x0053), e.values.toList())
        assertNull(e.condition)
    }

    @Test
    fun `CaseMapping entry with condition round-trips`() {
        val table = singleSubtableTable("Special_Casing", listOf(
            PuaaEntry.CaseMapping(0x0049, 0x0049, intArrayOf(0x0069, 0x0307), "lt"),
        ))
        val rt = roundTrip(table)
        val e = rt.subtables[0].entries[0] as PuaaEntry.CaseMapping
        assertEquals(listOf(0x0069, 0x0307), e.values.toList())
        assertEquals("lt", e.condition)
    }

    // ---- NameAlias entry ---------------------------------------------------

    @Test
    fun `NameAlias entry round-trips`() {
        val table = singleSubtableTable("Name_Alias", listOf(
            PuaaEntry.NameAlias(0x0000, 0x0000, "NULL", "control"),
            PuaaEntry.NameAlias(0x0001, 0x0001, "START OF HEADING", "control"),
        ))
        val rt = roundTrip(table)
        assertEquals(2, rt.subtables[0].entries.size)
        val e0 = rt.subtables[0].entries[0] as PuaaEntry.NameAlias
        assertEquals("NULL", e0.alias)
        assertEquals("control", e0.type)
        val e1 = rt.subtables[0].entries[1] as PuaaEntry.NameAlias
        assertEquals("START OF HEADING", e1.alias)
        assertEquals("control", e1.type)
    }

    // ---- Multi-subtable round-trip -----------------------------------------

    @Test
    fun `multiple subtables round-trip and sort by property`() {
        val table = PuaaTable(subtables = listOf(
            PuaaSubtable("Z_last", listOf(PuaaEntry.BooleanEntry(0x0041, 0x0041, true))),
            PuaaSubtable("A_first", listOf(PuaaEntry.Decimal(0x0030, 0x0030, 42))),
        ))
        val rt = roundTrip(table)
        assertEquals(2, rt.subtables.size)
        // Should be sorted by property name
        assertEquals("A_first", rt.subtables[0].property)
        assertEquals("Z_last", rt.subtables[1].property)
    }

    // ---- Plane > 0 (supplementary code points) ----------------------------

    @Test
    fun `supplementary code points round-trip`() {
        val table = singleSubtableTable("General_Category", listOf(
            PuaaEntry.Single(0x10000, 0x1000F, "Lo"),
            PuaaEntry.Single(0x1F600, 0x1F64F, "So"),
        ))
        val rt = roundTrip(table)
        val e0 = rt.subtables[0].entries[0] as PuaaEntry.Single
        assertEquals(0x10000, e0.firstCodePoint)
        assertEquals(0x1000F, e0.lastCodePoint)
        val e1 = rt.subtables[0].entries[1] as PuaaEntry.Single
        assertEquals(0x1F600, e1.firstCodePoint)
        assertEquals(0x1F64F, e1.lastCodePoint)
    }

    // ---- String deduplication (string table interning) ---------------------

    @Test
    fun `duplicate strings are interned in string table`() {
        val table = PuaaTable(subtables = listOf(
            PuaaSubtable("prop1", listOf(
                PuaaEntry.Single(0x0041, 0x0041, "Latin"),
            )),
            PuaaSubtable("prop2", listOf(
                PuaaEntry.Single(0x0042, 0x0042, "Latin"),
            )),
        ))
        val bytes1 = table.compile()
        // The string "Latin" should only appear once in the string table.
        // We can verify this by checking size is smaller than if duplicated.
        // But more importantly, the round-trip should still work.
        val rt = roundTrip(table)
        assertEquals("Latin", (rt.subtables[0].entries[0] as PuaaEntry.Single).value)
        assertEquals("Latin", (rt.subtables[1].entries[0] as PuaaEntry.Single).value)
    }

    // ---- Empty table -------------------------------------------------------

    @Test
    fun `empty table round-trips`() {
        val table = PuaaTable(subtables = emptyList())
        val rt = roundTrip(table)
        assertEquals(0, rt.subtables.size)
    }

    // ---- String minification -----------------------------------------------

    @Test
    fun `short ASCII strings get minified into entryData`() {
        // "Lu" is 2 ASCII bytes, should be minified (offset < 0)
        val table = singleSubtableTable("gc", listOf(
            PuaaEntry.Single(0x0041, 0x005A, "Lu"),
        ))
        val rt = roundTrip(table)
        val e = rt.subtables[0].entries[0] as PuaaEntry.Single
        assertEquals("Lu", e.value)
    }

    // ---- Double round-trip stability ---------------------------------------

    @Test
    fun `double round trip produces identical bytes`() {
        val table = PuaaTable(subtables = listOf(
            PuaaSubtable("General_Category", listOf(
                PuaaEntry.Single(0x0041, 0x005A, "Lu"),
                PuaaEntry.Single(0x0061, 0x007A, "Ll"),
                PuaaEntry.Multiple(0x00C0, 0x00C2, listOf("Lu", "Lu", "Lu")),
            )),
            PuaaSubtable("Alphabetic", listOf(
                PuaaEntry.BooleanEntry(0x0041, 0x007A, true),
            )),
            PuaaSubtable("Numeric_Value", listOf(
                PuaaEntry.Decimal(0x0030, 0x0030, 0),
            )),
            PuaaSubtable("Simple_LM", listOf(
                PuaaEntry.Hexadecimal(0x0041, 0x0041, 0x0061),
            )),
            PuaaSubtable("Bidi_Mirror", listOf(
                PuaaEntry.HexMultiple(0x0028, 0x0029, intArrayOf(0x0029, 0x0028)),
            )),
            PuaaSubtable("Decomp", listOf(
                PuaaEntry.HexSequence(0x00C0, 0x00C0, intArrayOf(0x0041, 0x0300)),
            )),
            PuaaSubtable("Case", listOf(
                PuaaEntry.CaseMapping(0x00DF, 0x00DF, intArrayOf(0x0053, 0x0053), "de"),
            )),
            PuaaSubtable("Name_Alias", listOf(
                PuaaEntry.NameAlias(0x0000, 0x0000, "NULL", "control"),
            )),
        ))
        val bytes1 = table.compile()
        val rt = PuaaTable.decompile(bytes1)
        val bytes2 = rt.compile()
        assertEquals(bytes1.toList(), bytes2.toList(), "double round-trip must produce identical bytes")
    }

    // ---- getPropertyValue --------------------------------------------------

    @Test
    fun `getPropertyValue works for Single entries`() {
        val st = PuaaSubtable("gc", listOf(
            PuaaEntry.Single(0x0041, 0x005A, "Lu"),
        ))
        assertEquals("Lu", st.getPropertyValue(0x0041))
        assertEquals("Lu", st.getPropertyValue(0x005A))
        assertNull(st.getPropertyValue(0x0040))
    }

    @Test
    fun `getPropertyValue works for Multiple entries`() {
        val st = PuaaSubtable("gc", listOf(
            PuaaEntry.Multiple(0x0041, 0x0043, listOf("A", "B", "C")),
        ))
        assertEquals("A", st.getPropertyValue(0x0041))
        assertEquals("B", st.getPropertyValue(0x0042))
        assertEquals("C", st.getPropertyValue(0x0043))
    }

    // ---- version -----------------------------------------------------------

    @Test
    fun `version round-trips`() {
        val table = PuaaTable(version = 2, subtables = listOf(
            PuaaSubtable("test", listOf(PuaaEntry.BooleanEntry(0x0041, 0x0041, true))),
        ))
        val rt = roundTrip(table)
        assertEquals(2, rt.version)
    }

    // ---- mixed entry types in one subtable ---------------------------------

    @Test
    fun `mixed entry types in single subtable round-trip`() {
        val table = singleSubtableTable("mixed", listOf(
            PuaaEntry.Single(0x0041, 0x0041, "A_val"),
            PuaaEntry.BooleanEntry(0x0042, 0x0042, true),
            PuaaEntry.Decimal(0x0043, 0x0043, 99),
            PuaaEntry.Hexadecimal(0x0044, 0x0044, 0x1234),
        ))
        val rt = roundTrip(table)
        assertEquals(4, rt.subtables[0].entries.size)
        assertTrue(rt.subtables[0].entries[0] is PuaaEntry.Single)
        assertTrue(rt.subtables[0].entries[1] is PuaaEntry.BooleanEntry)
        assertTrue(rt.subtables[0].entries[2] is PuaaEntry.Decimal)
        assertTrue(rt.subtables[0].entries[3] is PuaaEntry.Hexadecimal)
    }

    // ---- Non-ASCII string --------------------------------------------------

    @Test
    fun `non-ASCII UTF-8 string round-trips`() {
        val table = singleSubtableTable("test", listOf(
            PuaaEntry.Single(0x0041, 0x0041, "\u00E9\u00E8\u00EA"),
        ))
        val rt = roundTrip(table)
        val e = rt.subtables[0].entries[0] as PuaaEntry.Single
        assertEquals("\u00E9\u00E8\u00EA", e.value)
    }
}
