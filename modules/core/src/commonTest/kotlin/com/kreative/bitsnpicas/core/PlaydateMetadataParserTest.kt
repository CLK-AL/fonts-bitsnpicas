package com.kreative.bitsnpicas.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Stage S4 -- commonMain tests for the pure-Kotlin Playdate metadata parser.
 *
 * Tests inline `.fnt` metadata content (no file I/O or PNG loading).
 */
class PlaydateMetadataParserTest {

    // ---- basic property parsing ----------------------------------------

    @Test
    fun `parses tracking property`() {
        val fnt = "tracking=3"
        val meta = PlaydateMetadataParser.parse(fnt)
        assertEquals(3, meta.tracking)
    }

    @Test
    fun `tracking defaults to 1 when absent`() {
        val fnt = "-- comment line"
        val meta = PlaydateMetadataParser.parse(fnt)
        assertEquals(1, meta.tracking)
    }

    @Test
    fun `parses name property`() {
        val fnt = "name=MyFont"
        val meta = PlaydateMetadataParser.parse(fnt)
        assertEquals("MyFont", meta.name)
    }

    @Test
    fun `parses cell dimensions`() {
        val fnt = """
            width=16
            height=20
        """.trimIndent()
        val meta = PlaydateMetadataParser.parse(fnt)
        assertEquals(16, meta.cellWidth)
        assertEquals(20, meta.cellHeight)
    }

    // ---- metrics parsing -----------------------------------------------

    @Test
    fun `parses metrics JSON-style line`() {
        val fnt = """--metrics={"baseline":10,"xHeight":7,"capHeight":9}"""
        val meta = PlaydateMetadataParser.parse(fnt)
        assertEquals(10, meta.baseline)
        assertEquals(7, meta.xHeight)
        assertEquals(9, meta.capHeight)
    }

    @Test
    fun `parses metrics without dashes prefix`() {
        val fnt = """metrics={"baseline":12,"xHeight":8,"capHeight":10}"""
        val meta = PlaydateMetadataParser.parse(fnt)
        assertEquals(12, meta.baseline)
        assertEquals(8, meta.xHeight)
        assertEquals(10, meta.capHeight)
    }

    @Test
    fun `partial metrics only sets present fields`() {
        val fnt = """--metrics={"baseline":5}"""
        val meta = PlaydateMetadataParser.parse(fnt)
        assertEquals(5, meta.baseline)
        assertNull(meta.xHeight)
        assertNull(meta.capHeight)
    }

    // ---- glyph-width lines ---------------------------------------------

    @Test
    fun `parses single-character glyph widths`() {
        val fnt = "A\t8\nB\t9\nC\t7"
        val meta = PlaydateMetadataParser.parse(fnt)
        assertEquals(3, meta.glyphWidths.size)
        assertEquals(0x41 to 8, meta.glyphWidths[0])
        assertEquals(0x42 to 9, meta.glyphWidths[1])
        assertEquals(0x43 to 7, meta.glyphWidths[2])
    }

    @Test
    fun `parses space keyword as U+0020`() {
        val fnt = "space\t4"
        val meta = PlaydateMetadataParser.parse(fnt)
        assertEquals(1, meta.glyphWidths.size)
        assertEquals(0x20 to 4, meta.glyphWidths[0])
    }

    @Test
    fun `parses U+XXXX escape in glyph widths`() {
        val fnt = "U+00E9\t6"
        val meta = PlaydateMetadataParser.parse(fnt)
        assertEquals(1, meta.glyphWidths.size)
        assertEquals(0xE9 to 6, meta.glyphWidths[0])
    }

    // ---- kern pair lines -----------------------------------------------

    @Test
    fun `parses kern pairs (two codepoints)`() {
        val fnt = "AV\t-1\nWA\t-2"
        val meta = PlaydateMetadataParser.parse(fnt)
        assertEquals(2, meta.kernPairs.size)
        assertEquals(Triple(0x41, 0x56, -1), meta.kernPairs[0])
        assertEquals(Triple(0x57, 0x41, -2), meta.kernPairs[1])
    }

    // ---- comment handling ----------------------------------------------

    @Test
    fun `comment lines are skipped`() {
        val fnt = """
            tracking=2
            -- this is a comment
            A	8
        """.trimIndent()
        val meta = PlaydateMetadataParser.parse(fnt)
        assertEquals(2, meta.tracking)
        assertEquals(1, meta.glyphWidths.size)
    }

    // ---- combined full example -----------------------------------------

    @Test
    fun `full fnt metadata example`() {
        val fnt = """
            --name=TestFont
            tracking=2
            --metrics={"baseline":10,"xHeight":7,"capHeight":9}
            width=12
            height=16
            space	4
            A	8
            B	9
            AV	-1
        """.trimIndent()
        val meta = PlaydateMetadataParser.parse(fnt)
        assertEquals("TestFont", meta.name)
        assertEquals(2, meta.tracking)
        assertEquals(12, meta.cellWidth)
        assertEquals(16, meta.cellHeight)
        assertEquals(10, meta.baseline)
        assertEquals(7, meta.xHeight)
        assertEquals(9, meta.capHeight)
        assertEquals(3, meta.glyphWidths.size)
        assertEquals(0x20 to 4, meta.glyphWidths[0])
        assertEquals(0x41 to 8, meta.glyphWidths[1])
        assertEquals(0x42 to 9, meta.glyphWidths[2])
        assertEquals(1, meta.kernPairs.size)
        assertEquals(Triple(0x41, 0x56, -1), meta.kernPairs[0])
    }

    // ---- empty input ---------------------------------------------------

    @Test
    fun `empty input produces defaults`() {
        val meta = PlaydateMetadataParser.parse("")
        assertNull(meta.name)
        assertEquals(1, meta.tracking)
        assertNull(meta.cellWidth)
        assertNull(meta.cellHeight)
        assertNull(meta.baseline)
        assertNull(meta.xHeight)
        assertNull(meta.capHeight)
        assertEquals(0, meta.glyphWidths.size)
        assertEquals(0, meta.kernPairs.size)
    }

    // ---- splitCodePoints internal function ------------------------------

    @Test
    fun `splitCodePoints handles mixed input`() {
        val cps = PlaydateMetadataParser.splitCodePoints("spaceAU+0042")
        assertEquals(listOf(0x20, 0x41, 0x42), cps)
    }
}
