package com.kreative.bitsnpicas.coverage.truetype

import com.kreative.bitsnpicas.truetype.EblcGlyphIdOffsetPair
import com.kreative.bitsnpicas.truetype.EblcIndexSubtable
import com.kreative.bitsnpicas.truetype.EblcIndexSubtable2
import com.kreative.bitsnpicas.truetype.EblcIndexSubtable3
import com.kreative.bitsnpicas.truetype.EblcIndexSubtable4
import com.kreative.bitsnpicas.truetype.EblcIndexSubtable5
import com.kreative.bitsnpicas.truetype.EblcIndexSubtableHeader
import com.kreative.bitsnpicas.truetype.SbitBigGlyphMetrics
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * EblcIndexSubtable formats 2/3/4/5 round-trip via reflection-invoked
 * read/write. The header field is set up to match a small synthetic glyph
 * range, then we additionally exercise getOffsets / setOffsets and
 * getGlyphIdOffsetPairs / setGlyphIdOffsetPairs which form the public surface.
 */
class EblcIndexSubtableTest {

    private fun header(first: Int = 0, last: Int = 0, dataOffset: Int = 1000): EblcIndexSubtableHeader {
        val h = EblcIndexSubtableHeader()
        h.firstGlyphIndex = first
        h.lastGlyphIndex = last
        h.imageDataOffset = dataOffset
        return h
    }

    private fun roundTrip(st: EblcIndexSubtable) {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use {
            val w = EblcIndexSubtable::class.java.getDeclaredMethod("write", DataOutputStream::class.java)
            w.isAccessible = true
            w.invoke(st, it)
            it.flush()
        }
        val bytes = bos.toByteArray()
        val lengthMethod = EblcIndexSubtable::class.java.getDeclaredMethod("length")
        lengthMethod.isAccessible = true
        val expectedLen = lengthMethod.invoke(st) as Int
        assertEquals(expectedLen, bytes.size, "length() mismatch")

        // Round-trip into a new instance with same header.
        val out = st.javaClass.getDeclaredConstructor().newInstance() as EblcIndexSubtable
        out.header = st.header
        DataInputStream(ByteArrayInputStream(bytes)).use {
            val r = EblcIndexSubtable::class.java.getDeclaredMethod("read", DataInputStream::class.java)
            r.isAccessible = true
            r.invoke(out, it)
        }
        assertSubtablesEqual(st, out)
    }

    private fun assertSubtablesEqual(a: EblcIndexSubtable, b: EblcIndexSubtable) {
        val oa = a.offsets
        val ob = b.offsets
        assertEquals(oa.size, ob.size)
        for (i in oa.indices) assertEquals(oa[i], ob[i], "offset $i differs")
        val pa = a.glyphIdOffsetPairs
        val pb = b.glyphIdOffsetPairs
        assertEquals(pa.size, pb.size)
        for (i in pa.indices) {
            assertEquals(pa[i].glyphID, pb[i].glyphID, "pair $i glyphID")
            assertEquals(pa[i].offset, pb[i].offset, "pair $i offset")
        }
    }

    @Test
    fun format2_constant_size() {
        val st = EblcIndexSubtable2()
        st.header = header(first = 5, last = 7)
        st.imageSize = 32
        st.bigMetrics = SbitBigGlyphMetrics().apply {
            height = 8; width = 8; horiAdvance = 8; vertAdvance = 8
        }
        roundTrip(st)
        // setOffsets should compute imageSize from delta.
        st.setOffsets(intArrayOf(1000, 1064))
        assertEquals(64, st.imageSize)
        // getGlyphIdOffsetPairs returns range+1 entries.
        val pairs = st.glyphIdOffsetPairs
        assertEquals(4, pairs.size) // last - first + 2 = 7-5+2 = 4
        assertEquals(5, pairs[0].glyphID)
    }

    @Test
    fun format3_variable_offsets_2byte() {
        val st = EblcIndexSubtable3()
        st.header = header(first = 1, last = 3)
        // Pre-populate with range+1 offsets via setOffsets.
        st.setOffsets(intArrayOf(1000, 1004, 1010, 1018))
        roundTrip(st)
        // Pairs round-trip.
        val pairs = st.glyphIdOffsetPairs
        st.setGlyphIdOffsetPairs(pairs)
        assertEquals(4, st.size)
    }

    @Test
    fun format3_with_odd_size_pads_short() {
        // Odd-sized list triggers the padding write branch.
        val st = EblcIndexSubtable3()
        st.header = header(first = 0, last = 1) // size = 3 (odd)
        st.setOffsets(intArrayOf(1000, 1004, 1010))
        assertEquals(3, st.size)
        roundTrip(st) // length() must include the pad short.
    }

    @Test
    fun format4_glyphId_offset_pairs() {
        val st = EblcIndexSubtable4()
        st.header = header()
        val p1 = EblcGlyphIdOffsetPair(); p1.glyphID = 10; p1.offset = 0
        val p2 = EblcGlyphIdOffsetPair(); p2.glyphID = 12; p2.offset = 32
        val p3 = EblcGlyphIdOffsetPair(); p3.glyphID = 15; p3.offset = 96 // sentinel pair
        st.add(p1); st.add(p2); st.add(p3)
        roundTrip(st)
        assertEquals(3, st.offsets.size)

        // setGlyphIdOffsetPairs subtracts imageDataOffset, getGlyphIdOffsetPairs adds it back.
        val pairs = st.glyphIdOffsetPairs
        st.setGlyphIdOffsetPairs(pairs)
        // setOffsets path
        st.setOffsets(intArrayOf(2000, 2032, 2096))
    }

    @Test
    fun format5_constant_size_with_glyph_array() {
        val st = EblcIndexSubtable5()
        st.header = header(first = 1, last = 1, dataOffset = 500)
        st.imageSize = 16
        st.bigMetrics = SbitBigGlyphMetrics().apply {
            height = 4; width = 4; horiAdvance = 4; vertAdvance = 4
        }
        // Add 3 glyph IDs (odd to trigger pad branch in write/length).
        st.add(1); st.add(2); st.add(3)
        roundTrip(st)

        // Public surface.
        val pairs = st.glyphIdOffsetPairs
        assertEquals(4, pairs.size) // size + 1
        st.setOffsets(intArrayOf(500, 532)) // imageSize -> 32
        assertEquals(32, st.imageSize)

        // setGlyphIdOffsetPairs path - first pair sets first/last, then loop expands.
        val newPairs = arrayOf(
            EblcGlyphIdOffsetPair().apply { glyphID = 10; offset = 500 },
            EblcGlyphIdOffsetPair().apply { glyphID = 5; offset = 516 },
            EblcGlyphIdOffsetPair().apply { glyphID = 20; offset = 532 },
        )
        st.setGlyphIdOffsetPairs(newPairs)
        assertEquals(5, st.header.firstGlyphIndex)
        assertEquals(20, st.header.lastGlyphIndex)
        assertTrue(st.imageSize == 16)
    }

    @Test
    fun format5_with_even_size_skips_pad() {
        val st = EblcIndexSubtable5()
        st.header = header(first = 0, last = 0, dataOffset = 200)
        st.imageSize = 8
        st.bigMetrics = SbitBigGlyphMetrics()
        st.add(1); st.add(2) // even - no pad short
        roundTrip(st)
    }

    @Test
    fun format2_setGlyphIdOffsetPairs_recomputes_header() {
        val st = EblcIndexSubtable2()
        st.header = header(first = 0, last = 0, dataOffset = 100)
        st.imageSize = 8
        st.bigMetrics = SbitBigGlyphMetrics()
        val pairs = arrayOf(
            EblcGlyphIdOffsetPair().apply { glyphID = 5; offset = 100 },
            EblcGlyphIdOffsetPair().apply { glyphID = 6; offset = 132 },
            EblcGlyphIdOffsetPair().apply { glyphID = 7; offset = 164 },
        )
        st.setGlyphIdOffsetPairs(pairs)
        assertEquals(5, st.header.firstGlyphIndex)
        // last = first + length - 2 = 5 + 3 - 2 = 6
        assertEquals(6, st.header.lastGlyphIndex)
        assertEquals(32, st.imageSize)
    }

    @Test
    fun format4_setOffsets_stops_at_min_length() {
        val st = EblcIndexSubtable4()
        st.header = header(dataOffset = 100)
        for (i in 0..3) {
            val p = EblcGlyphIdOffsetPair(); p.glyphID = i; p.offset = i * 32
            st.add(p)
        }
        // Pass fewer offsets than the list size - should only update first 2.
        st.setOffsets(intArrayOf(200, 232))
        assertEquals(100, st[0].offset) // 200 - 100
        assertEquals(132, st[1].offset)
        assertEquals(64, st[2].offset)  // unchanged
    }
}
