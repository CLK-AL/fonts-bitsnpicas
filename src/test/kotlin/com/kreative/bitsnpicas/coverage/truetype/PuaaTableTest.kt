package com.kreative.bitsnpicas.coverage.truetype

import com.kreative.bitsnpicas.truetype.PuaaSubtable
import com.kreative.bitsnpicas.truetype.PuaaSubtableEntry
import com.kreative.bitsnpicas.truetype.PuaaTable
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Round-trip every PuaaSubtableEntry variant through PuaaTable.compile +
 * decompile to drive coverage of all entry-type branches in both methods.
 *
 * The entry classes (Single, Multiple, Boolean, Decimal, Hexadecimal,
 * HexMultiple, HexSequence, CaseMapping, NameAlias) each take a different
 * compile/decompile branch, so every variant must have at least one entry.
 */
class PuaaTableTest {

    private fun makeTable(): PuaaTable {
        val t = PuaaTable()

        val single = PuaaSubtable().apply { property = "Custom_Name" }
        single.add(PuaaSubtableEntry.Single().apply {
            firstCodePoint = 0xE000; lastCodePoint = 0xE000; value = "private-A"
        })
        single.add(PuaaSubtableEntry.Single().apply {
            firstCodePoint = 0xE001; lastCodePoint = 0xE001; value = "ab" // short - exercises minify branch
        })
        t.add(single)

        val multiple = PuaaSubtable().apply { property = "ZZ_Multiple" }
        multiple.add(PuaaSubtableEntry.Multiple().apply {
            firstCodePoint = 0xE100; lastCodePoint = 0xE101
            values = arrayOf("alpha", "beta")
        })
        t.add(multiple)

        val bool = PuaaSubtable().apply { property = "ZZ_Boolean" }
        bool.add(PuaaSubtableEntry.Boolean().apply {
            firstCodePoint = 0xE200; lastCodePoint = 0xE200; value = true
        })
        bool.add(PuaaSubtableEntry.Boolean().apply {
            firstCodePoint = 0xE201; lastCodePoint = 0xE201; value = false
        })
        t.add(bool)

        val decimal = PuaaSubtable().apply { property = "ZZ_Decimal" }
        decimal.add(PuaaSubtableEntry.Decimal().apply {
            firstCodePoint = 0xE300; lastCodePoint = 0xE300; value = 42
        })
        t.add(decimal)

        val hex = PuaaSubtable().apply { property = "ZZ_Hexadecimal" }
        hex.add(PuaaSubtableEntry.Hexadecimal().apply {
            firstCodePoint = 0xE400; lastCodePoint = 0xE400; value = 0xCAFE
        })
        t.add(hex)

        val hexMul = PuaaSubtable().apply { property = "ZZ_HexMul" }
        hexMul.add(PuaaSubtableEntry.HexMultiple().apply {
            firstCodePoint = 0xE500; lastCodePoint = 0xE501
            values = intArrayOf(0x1234, 0x5678)
        })
        t.add(hexMul)

        val hexSeq = PuaaSubtable().apply { property = "ZZ_HexSeq" }
        hexSeq.add(PuaaSubtableEntry.HexSequence().apply {
            firstCodePoint = 0xE600; lastCodePoint = 0xE600
            values = intArrayOf(0x41, 0x42, 0x43)
        })
        t.add(hexSeq)

        val caseMap = PuaaSubtable().apply { property = "ZZ_CaseMapping" }
        caseMap.add(PuaaSubtableEntry.CaseMapping().apply {
            firstCodePoint = 0xE700; lastCodePoint = 0xE700
            values = intArrayOf(0x53, 0x53)
            condition = "Final_Sigma"
        })
        t.add(caseMap)

        val nameAlias = PuaaSubtable().apply { property = "ZZ_NameAlias" }
        nameAlias.add(PuaaSubtableEntry.NameAlias().apply {
            firstCodePoint = 0xE800; lastCodePoint = 0xE800
            alias = "ALPHA"; type = "alternate"
        })
        t.add(nameAlias)

        return t
    }

    @Test
    fun puaaTable_compile_decompile_all_entry_variants() {
        val table = makeTable()
        val bytes = table.compile(emptyArray())
        assertTrue(bytes.isNotEmpty())

        val rt = PuaaTable()
        rt.decompile(bytes, emptyArray())
        // After decompile we get one subtable per property. Sort by property
        // (compile sorts so we don't depend on insertion order).
        val byProp = rt.associateBy { it.property }
        assertEquals(9, byProp.size)

        val single = byProp["Custom_Name"]!!
        assertEquals("private-A", single.getPropertyValue(0xE000))
        assertEquals("ab", single.getPropertyValue(0xE001))

        val multiple = byProp["ZZ_Multiple"]!!
        assertEquals("alpha", multiple.getPropertyValue(0xE100))
        assertEquals("beta", multiple.getPropertyValue(0xE101))

        val bool = byProp["ZZ_Boolean"]!!
        assertEquals("Y", bool.getPropertyValue(0xE200))
        assertEquals("N", bool.getPropertyValue(0xE201))

        assertEquals("42", byProp["ZZ_Decimal"]!!.getPropertyValue(0xE300))
        assertEquals("CAFE", byProp["ZZ_Hexadecimal"]!!.getPropertyValue(0xE400))

        val hexMul = byProp["ZZ_HexMul"]!!
        assertEquals("1234", hexMul.getPropertyValue(0xE500))
        assertEquals("5678", hexMul.getPropertyValue(0xE501))

        assertEquals("0041 0042 0043", byProp["ZZ_HexSeq"]!!.getPropertyValue(0xE600))
        assertEquals("0053 0053; Final_Sigma", byProp["ZZ_CaseMapping"]!!.getPropertyValue(0xE700))
        assertEquals("ALPHA;alternate", byProp["ZZ_NameAlias"]!!.getPropertyValue(0xE800))
    }

    @Test
    fun puaaTable_table_metadata() {
        val t = PuaaTable()
        assertEquals("PUAA", t.tableName())
        assertTrue(t.dependencyNames().isEmpty())
        assertEquals(PuaaTable.DEFAULT_VERSION, t.version)

        // getOrCreate returns existing.
        val s = t.getOrCreateSubtable("X")
        assertNotNull(s)
        val s2 = t.getOrCreateSubtable("X")
        assertTrue(s === s2)
        assertNull(t.getOrCreateSubtable(null))
        assertNull(t.getSubtable(null))
        assertNull(t.getSubtable("nope"))
        assertEquals(s, t.getSubtable("X"))
        assertNull(t.getPropertyValue(null, 0))
        assertNull(t.getPropertyValue("nope", 0))
        // Empty subtable returns null for any code point.
        assertNull(t.getPropertyValue("X", 0x41))
    }

    @Test
    fun puaaTable_removeEmpty_and_sort_helpers() {
        val t = PuaaTable()
        // Add a real subtable.
        val real = PuaaSubtable().apply { property = "Real" }
        real.add(PuaaSubtableEntry.Single().apply {
            firstCodePoint = 1; lastCodePoint = 1; value = "x"
        })
        t.add(real)
        // Add an empty subtable.
        t.add(PuaaSubtable().apply { property = "Empty" })
        // Add a null entry.
        t.add(null)
        t.removeEmptySubtables()
        assertEquals(1, t.size)
        // sortSubtables sorts entries within isSortable subtables.
        t.sortSubtables()
    }

    @Test
    fun puaaSubtable_isSortable_overlapping_returns_false() {
        val s = PuaaSubtable().apply { property = "X" }
        s.add(PuaaSubtableEntry.Single().apply {
            firstCodePoint = 0; lastCodePoint = 5; value = "a"
        })
        s.add(PuaaSubtableEntry.Single().apply {
            firstCodePoint = 3; lastCodePoint = 8; value = "b" // overlaps
        })
        assertTrue(!s.isSortable)
    }

    @Test
    fun puaaTable_compile_decompile_with_repeated_string_values() {
        // Multiple Single entries with the same value should re-use the
        // string-table offset on compile, and decompile back equivalently.
        val t = PuaaTable()
        val s = PuaaSubtable().apply { property = "Reuse" }
        for (cp in 0xE900..0xE905) {
            s.add(PuaaSubtableEntry.Single().apply {
                firstCodePoint = cp; lastCodePoint = cp; value = "shared-string-value"
            })
        }
        t.add(s)
        val bytes = t.compile(emptyArray())
        val rt = PuaaTable()
        rt.decompile(bytes, emptyArray())
        for (cp in 0xE900..0xE905) {
            assertEquals("shared-string-value", rt[0].getPropertyValue(cp))
        }
    }
}
