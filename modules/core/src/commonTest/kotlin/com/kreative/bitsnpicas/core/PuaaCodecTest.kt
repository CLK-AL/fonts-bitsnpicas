package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.puaa.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Round-trip tests for PUAA text-format codecs.
 *
 * For each codec we:
 *   1. Provide minimal representative text input.
 *   2. Compile into a [PuaaTable] via the codec.
 *   3. Decompile back to text lines.
 *   4. Assert structural equivalence.
 */
class PuaaCodecTest {

    // ---- BlocksCodec --------------------------------------------------------

    @Test
    fun `BlocksCodec round-trips a minimal Blocks file`() {
        val input = listOf(
            "# Blocks-15.0.0.txt",
            "# Date: 2022-01-28, 04:00:00 GMT",
            "",
            "0000..007F; Basic Latin",
            "0080..00FF; Latin-1 Supplement",
            "0100..024F; Latin Extended-A",
        )

        val codec = BlocksCodec()
        val table = compileCodec(codec, input)

        // Verify the table has the Block subtable with 3 entries.
        val blockSt = table.subtables.firstOrNull { it.property == "Block" }
        assertNotNull(blockSt, "Block subtable should exist")
        assertEquals(3, blockSt.entries.size, "Expected 3 block entries")

        // Verify lookup.
        assertEquals("Basic Latin", blockSt.getPropertyValue(0x0041))
        assertEquals("Latin-1 Supplement", blockSt.getPropertyValue(0x00C0))
        assertEquals("Latin Extended-A", blockSt.getPropertyValue(0x0100))

        // Round-trip through decompile.
        val output = codec.decompile(table)
        assertEquals(3, output.size, "Expected 3 output lines")
        assertEquals("0000..007F; Basic Latin", output[0])
        assertEquals("0080..00FF; Latin-1 Supplement", output[1])
        assertEquals("0100..024F; Latin Extended-A", output[2])
    }

    // ---- PropListCodec ------------------------------------------------------

    @Test
    fun `PropListCodec round-trips boolean properties`() {
        val input = listOf(
            "0009..000D    ; White_Space # Cc   [5] <control-0009>..<control-000D>",
            "0020          ; White_Space # Zs       SPACE",
            "0030..0039    ; Hex_Digit # Nd  [10] DIGIT ZERO..DIGIT NINE",
            "0041..0046    ; Hex_Digit # Lu   [6] LATIN CAPITAL LETTER A..F",
        )

        val codec = PropListCodec()
        val table = compileCodec(codec, input)

        // White_Space subtable.
        val wsSt = table.subtables.firstOrNull { it.property == "White_Space" }
        assertNotNull(wsSt, "White_Space subtable should exist")
        assertEquals("Y", wsSt.getPropertyValue(0x0009))
        assertEquals("Y", wsSt.getPropertyValue(0x0020))

        // Hex_Digit subtable.
        val hexSt = table.subtables.firstOrNull { it.property == "Hex_Digit" }
        assertNotNull(hexSt, "Hex_Digit subtable should exist")
        assertEquals("Y", hexSt.getPropertyValue(0x0030))
        assertEquals("Y", hexSt.getPropertyValue(0x0041))

        // Round-trip.
        val output = codec.decompile(table)
        assertTrue(output.isNotEmpty(), "Decompiled output should not be empty")
        // Output should contain both White_Space and Hex_Digit entries.
        assertTrue(output.any { "White_Space" in it }, "Should have White_Space output")
        assertTrue(output.any { "Hex_Digit" in it }, "Should have Hex_Digit output")
    }

    // ---- ScriptsCodec -------------------------------------------------------

    @Test
    fun `ScriptsCodec round-trips script assignments`() {
        val input = listOf(
            "0041..005A    ; Latin",
            "0061..007A    ; Latin",
            "0370..0373    ; Greek",
        )

        val codec = ScriptsCodec()
        val table = compileCodec(codec, input)

        val st = table.subtables.firstOrNull { it.property == "Script" }
        assertNotNull(st, "Script subtable should exist")
        assertEquals("Latin", st.getPropertyValue(0x0041))
        assertEquals("Latin", st.getPropertyValue(0x006A))
        assertEquals("Greek", st.getPropertyValue(0x0370))

        val output = codec.decompile(table)
        assertTrue(output.isNotEmpty())
        // Latin entries should come before Greek (lower code point first).
        val latinIdx = output.indexOfFirst { "Latin" in it }
        val greekIdx = output.indexOfFirst { "Greek" in it }
        assertTrue(latinIdx < greekIdx, "Latin entries should precede Greek entries")
    }

    // ---- UnicodeDataCodec ---------------------------------------------------

    @Test
    fun `UnicodeDataCodec round-trips a minimal UnicodeData excerpt`() {
        val input = listOf(
            "0041;LATIN CAPITAL LETTER A;Lu;0;L;;;;;N;;;;0061;",
            "0061;LATIN SMALL LETTER A;Ll;0;L;;;;;N;;;0041;;0041",
            "0042;LATIN CAPITAL LETTER B;Lu;0;L;;;;;N;;;;0062;",
        )

        val codec = UnicodeDataCodec()
        val table = compileCodec(codec, input)

        // Name.
        val nameSt = table.subtables.firstOrNull { it.property == "Name" }
        assertNotNull(nameSt, "Name subtable should exist")
        assertEquals("LATIN CAPITAL LETTER A", nameSt.getPropertyValue(0x0041))
        assertEquals("LATIN SMALL LETTER A", nameSt.getPropertyValue(0x0061))

        // General_Category.
        val catSt = table.subtables.firstOrNull { it.property == "General_Category" }
        assertNotNull(catSt, "General_Category subtable should exist")
        assertEquals("Lu", catSt.getPropertyValue(0x0041))
        assertEquals("Ll", catSt.getPropertyValue(0x0061))

        // Bidi_Mirrored.
        val bidiSt = table.subtables.firstOrNull { it.property == "Bidi_Mirrored" }
        assertNotNull(bidiSt, "Bidi_Mirrored subtable should exist")
        assertEquals("N", bidiSt.getPropertyValue(0x0041))

        // Simple_Lowercase_Mapping.
        val lcSt = table.subtables.firstOrNull { it.property == "Simple_Lowercase_Mapping" }
        assertNotNull(lcSt, "Simple_Lowercase_Mapping subtable should exist")
        assertEquals("0061", lcSt.getPropertyValue(0x0041))

        // Round-trip: decompile and re-compile.
        val output = codec.decompile(table)
        assertEquals(3, output.size, "Expected 3 output lines")

        val table2 = compileCodec(codec, output)
        val nameSt2 = table2.subtables.firstOrNull { it.property == "Name" }
        assertNotNull(nameSt2)
        assertEquals("LATIN CAPITAL LETTER A", nameSt2.getPropertyValue(0x0041))
    }

    // ---- PuaaCodecRegistry --------------------------------------------------

    @Test
    fun `PuaaCodecRegistry finds registered codecs by filename`() {
        val registry = PuaaCodecRegistry.instance
        assertNotNull(registry.getCodec("Blocks.txt"))
        assertNotNull(registry.getCodec("PropList.txt"))
        assertNotNull(registry.getCodec("Scripts.txt"))
        assertNotNull(registry.getCodec("UnicodeData.txt"))
        // Case-insensitive lookup.
        assertNotNull(registry.getCodec("blocks.txt"))
        assertEquals(4, registry.getCodecs().size)
    }

    // ---- Helpers ------------------------------------------------------------

    private fun compileCodec(codec: PuaaCodec, lines: List<String>): PuaaTable {
        val mutable = MutablePuaaTable()
        codec.compile(mutable, lines.asSequence())
        return mutable.toTable()
    }
}
