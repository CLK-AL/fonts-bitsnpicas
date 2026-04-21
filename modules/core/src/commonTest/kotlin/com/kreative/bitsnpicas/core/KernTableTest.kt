package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.truetype.KernTable
import com.kreative.bitsnpicas.core.truetype.KernFormat0Subtable
import com.kreative.bitsnpicas.core.truetype.KernPair
import com.kreative.bitsnpicas.core.truetype.UnknownKernSubtable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KernTableTest {

    @Test
    fun roundTripFormat0() {
        val kern = KernTable()
        kern.version = 0

        val st = KernFormat0Subtable()
        st.subtableVersion = 0
        st.coverage = 0x0001 // format 0, horizontal
        st.pairs.add(KernPair(left = 36, right = 40, value = -50))
        st.pairs.add(KernPair(left = 36, right = 55, value = -30))
        st.pairs.add(KernPair(left = 55, right = 36, value = -20))
        kern.subtables.add(st)

        val compiled = kern.compile()
        val kern2 = KernTable()
        kern2.decompile(compiled)

        assertEquals(0, kern2.version)
        assertEquals(1, kern2.subtables.size)
        val st2 = kern2.subtables[0]
        assertIs<KernFormat0Subtable>(st2)
        assertEquals(0x0001, st2.coverage)
        assertEquals(3, st2.pairs.size)
        assertEquals(KernPair(36, 40, -50), st2.pairs[0])
        assertEquals(KernPair(36, 55, -30), st2.pairs[1])
        assertEquals(KernPair(55, 36, -20), st2.pairs[2])
    }

    @Test
    fun roundTripMultipleSubtables() {
        val kern = KernTable()
        kern.version = 0

        // Format 0 subtable
        val st1 = KernFormat0Subtable()
        st1.subtableVersion = 0
        st1.coverage = 0x0001
        st1.pairs.add(KernPair(left = 1, right = 2, value = -10))
        kern.subtables.add(st1)

        // Unknown format subtable (format 2 = coverage bits 8-15 = 2)
        val st2 = UnknownKernSubtable(ByteArray(8) { (it + 1).toByte() })
        st2.subtableVersion = 0
        st2.coverage = 0x0200 // format 2
        kern.subtables.add(st2)

        val compiled = kern.compile()
        val kern2 = KernTable()
        kern2.decompile(compiled)

        assertEquals(2, kern2.subtables.size)
        assertIs<KernFormat0Subtable>(kern2.subtables[0])
        assertIs<UnknownKernSubtable>(kern2.subtables[1])
        assertEquals(0x0200, kern2.subtables[1].coverage)
    }

    @Test
    fun roundTripNegativeValues() {
        val kern = KernTable()
        val st = KernFormat0Subtable()
        st.coverage = 0x0001
        st.pairs.add(KernPair(left = 10, right = 20, value = -32768))
        st.pairs.add(KernPair(left = 30, right = 40, value = 32767))
        st.pairs.add(KernPair(left = 50, right = 60, value = 0))
        kern.subtables.add(st)

        val compiled = kern.compile()
        val kern2 = KernTable()
        kern2.decompile(compiled)

        val st2 = kern2.subtables[0] as KernFormat0Subtable
        assertEquals(-32768, st2.pairs[0].value)
        assertEquals(32767, st2.pairs[1].value)
        assertEquals(0, st2.pairs[2].value)
    }

    @Test
    fun roundTripEmpty() {
        val kern = KernTable()
        kern.version = 0
        val compiled = kern.compile()
        val kern2 = KernTable()
        kern2.decompile(compiled)
        assertEquals(0, kern2.version)
        assertEquals(0, kern2.subtables.size)
    }

    @Test
    fun tableIdMatchesTag() {
        val t = KernTable()
        assertEquals("kern", t.tableName)
        assertEquals(0x6B65726E, t.tableId)
    }
}
