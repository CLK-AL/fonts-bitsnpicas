package com.kreative.bitsnpicas.coverage.truetype

import com.kreative.bitsnpicas.truetype.CmapSubtableFormat0
import com.kreative.bitsnpicas.truetype.CmapSubtableFormat6
import com.kreative.bitsnpicas.truetype.CmapSubtableFormat10
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Direct decompile/compile round-trip for the smaller cmap subtable formats
 * (0, 6, 10) to lift truetype line/branch coverage. Each test crafts a
 * minimal valid byte array, parses it via decompile(byte[]), asserts the
 * parsed state, then re-emits via compile() and asserts byte equality.
 *
 * Format 12 is already exercised indirectly via TTF round-trip.
 */
class CmapSubtableFormatsTest {

    @Test
    fun cmapFormat0_round_trip_and_lookup() {
        // Build a 262-byte format-0 subtable: format(2) length(2) lang(2) + 256 bytes.
        val data = ByteArray(262)
        data[0] = 0; data[1] = 0           // format = 0
        data[2] = 0x01; data[3] = 0x06     // length = 262
        data[4] = 0; data[5] = 0           // language ID = 0
        // map glyph index 5 to char 'A' (0x41), and 9 to 'B'.
        data[6 + 0x41] = 5
        data[6 + 0x42] = 9

        val st = CmapSubtableFormat0()
        st.decompile(data)
        assertEquals(0, st.format())
        assertEquals(0, st.languageID)
        assertEquals(5, st.getGlyphIndex(0x41))
        assertEquals(9, st.getGlyphIndex(0x42))
        // Out-of-range lookup returns 0.
        assertEquals(0, st.getGlyphIndex(-1))
        assertEquals(0, st.getGlyphIndex(0x100))

        val out = st.compile()
        assertEquals(262, out.size)
        // Bytes round-trip.
        for (i in data.indices) assertEquals(data[i], out[i], "diff at $i")
    }

    @Test
    fun cmapFormat6_round_trip_and_lookup() {
        // format(2) length(2) lang(2) firstChar(2) entryCount(2) + entryCount * 2.
        val n = 4
        val out = java.io.ByteArrayOutputStream()
        val d = java.io.DataOutputStream(out)
        d.writeShort(6)
        d.writeShort(10 + n * 2)
        d.writeShort(0) // languageID
        d.writeShort(0x30) // firstChar = '0'
        d.writeShort(n)
        for (g in intArrayOf(11, 12, 13, 14)) d.writeShort(g)
        d.flush()
        val data = out.toByteArray()

        val st = CmapSubtableFormat6()
        st.decompile(data)
        assertEquals(6, st.format())
        assertEquals(0x30, st.firstChar)
        assertEquals(4, st.glyphIndex.size)
        assertEquals(11, st.getGlyphIndex(0x30))
        assertEquals(14, st.getGlyphIndex(0x33))
        assertEquals(0, st.getGlyphIndex(0x29)) // below
        assertEquals(0, st.getGlyphIndex(0x40)) // above

        val recompiled = st.compile()
        for (i in data.indices) assertEquals(data[i], recompiled[i], "diff at $i")
    }

    @Test
    fun cmapFormat10_round_trip_and_lookup() {
        // format(2) subformat(2) length(4) lang(4) firstChar(4) entryCount(4) + entryCount*2.
        val n = 3
        val out = java.io.ByteArrayOutputStream()
        val d = java.io.DataOutputStream(out)
        d.writeShort(10)
        d.writeShort(0)              // subformat
        d.writeInt(20 + n * 2)       // length
        d.writeInt(0)                // language
        d.writeInt(0x10000)          // firstChar - SMP
        d.writeInt(n)
        for (g in intArrayOf(20, 21, 22)) d.writeShort(g)
        d.flush()
        val data = out.toByteArray()

        val st = CmapSubtableFormat10()
        st.decompile(data)
        assertEquals(10, st.format())
        assertEquals(0x10000, st.firstChar)
        assertEquals(3, st.glyphIndex.size)
        assertEquals(20, st.getGlyphIndex(0x10000))
        assertEquals(22, st.getGlyphIndex(0x10002))
        assertEquals(0, st.getGlyphIndex(0xFFFF))
        assertEquals(0, st.getGlyphIndex(0x10010))

        val recompiled = st.compile()
        for (i in data.indices) assertEquals(data[i], recompiled[i], "diff at $i")
    }
}
