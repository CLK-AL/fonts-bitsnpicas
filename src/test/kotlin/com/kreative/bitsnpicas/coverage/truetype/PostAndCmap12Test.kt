package com.kreative.bitsnpicas.coverage.truetype

import com.kreative.bitsnpicas.truetype.CmapSubtableFormat12
import com.kreative.bitsnpicas.truetype.CmapSubtableSequentialEntry
import com.kreative.bitsnpicas.truetype.PostTable
import com.kreative.bitsnpicas.truetype.PostTableEntry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Round-trip tests for PostTable formats 1/2/2.5/3/4 and
 * CmapSubtableFormat12.
 */
class PostAndCmap12Test {

    @Test
    fun postTable_format1_round_trip() {
        val t = PostTable()
        t.format = PostTable.FORMAT_1
        t.italicAngle = PostTable.ITALIC_ANGLE_UPRIGHT
        val bytes = t.compile(emptyArray())
        val rt = PostTable()
        rt.decompile(bytes, emptyArray())
        assertEquals(PostTable.FORMAT_1, rt.format)
        assertEquals(0, rt.size)
    }

    @Test
    fun postTable_format2_round_trip_with_strings_and_ints() {
        val t = PostTable()
        t.format = PostTable.FORMAT_2
        t.italicAngle = PostTable.ITALIC_ANGLE_ISOMETRIC
        t.underlinePosition = -100
        t.underlineThickness = 50
        t.fixedPitch = PostTable.FIXED_PITCH_TRUE
        t.add(PostTableEntry(0))
        t.add(PostTableEntry(3)) // standard space
        t.add(PostTableEntry("custom-glyph"))
        t.add(PostTableEntry("custom-glyph-2"))
        val bytes = t.compile(emptyArray())
        val rt = PostTable()
        rt.decompile(bytes, emptyArray())
        assertEquals(PostTable.FORMAT_2, rt.format)
        assertEquals(4, rt.size)
        assertEquals(0, rt[0].intValue())
        assertEquals(3, rt[1].intValue())
        assertEquals("custom-glyph", rt[2].stringValue())
        assertEquals("custom-glyph-2", rt[3].stringValue())
        assertEquals(PostTable.ITALIC_ANGLE_ISOMETRIC, rt.italicAngle)
        assertEquals(-100, rt.underlinePosition)
    }

    @Test
    fun postTable_format2_5_round_trip_int_only() {
        val t = PostTable()
        t.format = PostTable.FORMAT_2_5
        t.add(PostTableEntry(5))
        t.add(PostTableEntry(7))
        t.add(PostTableEntry(9))
        val bytes = t.compile(emptyArray())
        val rt = PostTable()
        rt.decompile(bytes, emptyArray())
        assertEquals(PostTable.FORMAT_2_5, rt.format)
        assertEquals(3, rt.size)
        assertEquals(5, rt[0].intValue())
        assertEquals(7, rt[1].intValue())
        assertEquals(9, rt[2].intValue())
    }

    @Test
    fun postTable_format3_round_trip_no_glyph_names() {
        val t = PostTable()
        t.format = PostTable.FORMAT_3
        val bytes = t.compile(emptyArray())
        val rt = PostTable()
        rt.decompile(bytes, emptyArray())
        assertEquals(PostTable.FORMAT_3, rt.format)
        assertEquals(0, rt.size)
    }

    @Test
    fun postTable_format4_round_trip_with_sentinel() {
        val t = PostTable()
        t.format = PostTable.FORMAT_4
        t.add(PostTableEntry(0x1234))
        t.add(PostTableEntry(0xFFFF))   // becomes -1 on decode
        t.add(PostTableEntry(0x0001))
        val bytes = t.compile(emptyArray())
        val rt = PostTable()
        rt.decompile(bytes, emptyArray())
        assertEquals(PostTable.FORMAT_4, rt.format)
        assertEquals(3, rt.size)
        assertEquals(0x1234, rt[0].intValue())
        assertEquals(-1, rt[1].intValue())
        assertEquals(0x0001, rt[2].intValue())
    }

    @Test
    fun cmapFormat12_round_trip_and_lookup() {
        val st = CmapSubtableFormat12()
        st.languageID = 0
        val e1 = CmapSubtableSequentialEntry().apply { startCharCode = 0x10000; endCharCode = 0x10005; glyphIndex = 100 }
        val e2 = CmapSubtableSequentialEntry().apply { startCharCode = 0x20000; endCharCode = 0x20003; glyphIndex = 200 }
        st.add(e1); st.add(e2)
        val bytes = st.compile()
        // Re-parse via decompile.
        val st2 = CmapSubtableFormat12()
        st2.decompile(bytes)
        assertEquals(12, st2.format())
        assertEquals(2, st2.size)
        assertEquals(100, st2.getGlyphIndex(0x10000))
        assertEquals(102, st2.getGlyphIndex(0x10002))
        assertEquals(200, st2.getGlyphIndex(0x20000))
        assertEquals(0, st2.getGlyphIndex(0x30000))   // outside any entry
    }

    @Test
    fun postTable_metadata() {
        val t = PostTable()
        assertEquals("post", t.tableName())
        assertTrue(t.dependencyNames().isEmpty())
    }
}
