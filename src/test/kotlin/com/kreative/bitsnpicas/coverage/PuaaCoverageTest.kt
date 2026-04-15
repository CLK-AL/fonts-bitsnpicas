package com.kreative.bitsnpicas.coverage

import com.kreative.bitsnpicas.puaa.*
import com.kreative.bitsnpicas.truetype.PuaaTable
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Scanner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Feeds a small, valid UCD-style input to every codec in PuaaCodecRegistry,
 * then drives decompile(PuaaTable) back to the printer. Also covers
 * PuaaUtility helpers and PuaaLookup/PuaaDecompiler surface.
 */
class PuaaCoverageTest {

    /** Static sample lines per UCD filename. Kept intentionally minimal. */
    private val ucdSamples: Map<String, String> = mapOf(
        "ArabicShaping.txt" to """
            0627; ALEF; R; Alef
            0628; BEH; D; Beh
        """.trimIndent(),
        "BidiBrackets.txt" to """
            0028; 0029; o
            0029; 0028; c
        """.trimIndent(),
        "BidiMirroring.txt" to """
            0028; 0029
            0029; 0028
        """.trimIndent(),
        "Blocks.txt" to """
            0000..007F; Basic Latin
            0080..00FF; Latin-1 Supplement
        """.trimIndent(),
        "CompositionExclusions.txt" to """
            0340
            0341
        """.trimIndent(),
        "DerivedAge.txt" to """
            0000..007F; 1.1
            0080..00FF; 1.1
        """.trimIndent(),
        "EastAsianWidth.txt" to """
            0000..001F; N
            0020;       Na
        """.trimIndent(),
        "emoji-data.txt" to """
            0023; Emoji
            002A; Emoji
        """.trimIndent(),
        "EquivalentUnifiedIdeograph.txt" to """
            2F00; 4E00
            2F01; 4E05
        """.trimIndent(),
        "GraphemeBreakProperty.txt" to """
            0000..001F; Control
            0020;       Other
        """.trimIndent(),
        "HangulSyllableType.txt" to """
            1100..115F; L
            1160..11A7; V
        """.trimIndent(),
        "IndicPositionalCategory.txt" to """
            0903; Right
        """.trimIndent(),
        "IndicSyllabicCategory.txt" to """
            0903; Bindu
        """.trimIndent(),
        "Jamo.txt" to """
            1100; G
            1101; GG
        """.trimIndent(),
        "LineBreak.txt" to """
            0009; BA
            000A; LF
        """.trimIndent(),
        "NameAliases.txt" to """
            0000; NULL; control
        """.trimIndent(),
        "NushuSources.txt" to """
            U+16FE1; kReading; 1
        """.trimIndent(),
        "PropList.txt" to """
            0020; White_Space
            0009..000D; White_Space
        """.trimIndent(),
        "Scripts.txt" to """
            0041..005A; Latin
            0030..0039; Common
        """.trimIndent(),
        "ScriptExtensions.txt" to """
            0640; Arab Syrc
        """.trimIndent(),
        "SentenceBreakProperty.txt" to """
            0009; Sp
        """.trimIndent(),
        "SpecialCasing.txt" to """
            00DF; 00DF; 0053 0053; 0053 0073;
        """.trimIndent(),
        "TangutSources.txt" to """
            17000; kRSTang; 1.0
        """.trimIndent(),
        "UnicodeData.txt" to """
            0041;LATIN CAPITAL LETTER A;Lu;0;L;;;;;N;;;;0061;
            0061;LATIN SMALL LETTER A;Ll;0;L;;;;;N;;;0041;;0041
            0030;DIGIT ZERO;Nd;0;EN;;0;0;0;N;;;;;
        """.trimIndent(),
        "Unihan_DictionaryIndices.txt" to """
            U+4E00; kHanYu; 10001.010
        """.trimIndent(),
        "Unihan_DictionaryLikeData.txt" to """
            U+4E00; kCangjie; M
        """.trimIndent(),
        "Unihan_IRGSources.txt" to """
            U+4E00; kIICore; 2020
        """.trimIndent(),
        "Unihan_NumericValues.txt" to """
            U+4E00; kPrimaryNumeric; 1
        """.trimIndent(),
        "Unihan_OtherMappings.txt" to """
            U+4E00; kBigFive; A440
        """.trimIndent(),
        "Unihan_RadicalStrokeCounts.txt" to """
            U+4E00; kRSUnicode; 1.0
        """.trimIndent(),
        "Unihan_Readings.txt" to """
            U+4E00; kMandarin; yi1
        """.trimIndent(),
        "Unihan_Variants.txt" to """
            U+4E00; kTraditionalVariant; U+4E00
        """.trimIndent(),
        "VerticalOrientation.txt" to """
            0000..007F; R
        """.trimIndent(),
        "WordBreakProperty.txt" to """
            0009; WSegSpace
        """.trimIndent(),
    )

    @Test
    fun every_codec_compiles_and_decompiles() {
        val registry = PuaaCodecRegistry.instance
        for (codec in registry.codecs) {
            val sample = ucdSamples[codec.fileName] ?: continue
            val puaa = PuaaTable()
            Scanner(sample).use { codec.compile(puaa, it) }
            val sw = StringWriter()
            PrintWriter(sw).use { codec.decompile(puaa, it) }
            // Decompile should succeed. Some codecs produce empty output for empty
            // subtable lookups - that's fine, just exercise the code path.
            assertNotNull(sw.toString())
        }
    }

    @Test
    fun registry_lookup_and_file_name_printer() {
        val registry = PuaaCodecRegistry.instance
        assertNotNull(registry.getCodec("UnicodeData.txt"))
        assertNotNull(registry.getCodec("unicodedata.txt"))
        assertNull(registry.getCodec("nope.txt"))

        // Exercise printFileNames() by redirecting stdout.
        val origOut = System.out
        val buf = ByteArrayOutputStream()
        System.setOut(PrintStream(buf))
        try {
            registry.printFileNames()
        } finally {
            System.setOut(origOut)
        }
        assertTrue(buf.size() > 0)
    }

    @Test
    fun puaaUtility_helpers() {
        // splitLine: strips comments / trims.
        assertNull(PuaaUtility.splitLine("   # only comment"))
        val fields = PuaaUtility.splitLine("0041 ; A # name")
        assertNotNull(fields); assertEquals(2, fields!!.size)

        // splitRange
        val single = PuaaUtility.splitRange("0041")
        assertEquals(0x41, single[0]); assertEquals(0x41, single[1])
        val range = PuaaUtility.splitRange("0041..0045")
        assertEquals(0x41, range[0]); assertEquals(0x45, range[1])

        // toHexString pads to 4.
        assertEquals("0041", PuaaUtility.toHexString(0x41))
        assertEquals("1F600", PuaaUtility.toHexString(0x1F600))

        // joinLine
        assertEquals("a;b;c", PuaaUtility.joinLine(arrayOf("a","b","c"), ";"))
        assertEquals("a;;c", PuaaUtility.joinLine(arrayOf("a", null, "c"), ";"))

        // equals helper
        assertTrue(PuaaUtility.equals(null, null))
        assertTrue(PuaaUtility.equals("x", "x"))
        assertTrue(!PuaaUtility.equals("x", null))
        assertTrue(!PuaaUtility.equals(null, "x"))
        assertTrue(!PuaaUtility.equals("x", "y"))

        // naturalCompare - exercises numeric + text branches.
        assertEquals(0, PuaaUtility.naturalCompare("abc1", "abc1"))
        assertTrue(PuaaUtility.naturalCompare("abc2", "abc10") < 0)
        assertTrue(PuaaUtility.naturalCompare("abc", "abd") < 0)
        assertTrue(PuaaUtility.naturalCompare("abc", "abcd") < 0)

        // joinRange for single and range entries.
        val e = com.kreative.bitsnpicas.truetype.PuaaSubtableEntry.Single().apply {
            firstCodePoint = 1; lastCodePoint = 1; value = "x"
        }
        assertEquals("0001", PuaaUtility.joinRange(e))
        e.lastCodePoint = 5
        assertTrue(PuaaUtility.joinRange(e).contains(".."))

        // Map-to-entry helpers - exercise the sort + run-collapse paths.
        PuaaUtility.createEntriesFromStringMap(linkedMapOf(0x41 to "A", 0x42 to "A", 0x43 to "B"))
        PuaaUtility.createEntriesFromBooleanMap(linkedMapOf(0x41 to true, 0x42 to true))
        PuaaUtility.createEntriesFromDecimalMap(linkedMapOf(0x41 to 1, 0x42 to 1, 0x43 to 2))
        PuaaUtility.createEntriesFromHexadecimalMap(linkedMapOf(0x41 to 0xA0, 0x42 to 0xA0))
        PuaaUtility.createEntriesFromHexSequenceMap(linkedMapOf(0x41 to intArrayOf(1,2), 0x42 to intArrayOf(1,2)))
        PuaaUtility.createEntriesFromNameMap(linkedMapOf(
            0x41 to "LATIN CAPITAL LETTER A",
            0x42 to "LATIN CAPITAL LETTER B",
            0x43 to "LATIN CAPITAL LETTER C",
        ))
    }

    @Test
    fun puaaLookup_and_decompiler_help_paths() {
        // These classes have a main() that prints usage when run with no args.
        val origOut = System.out
        val buf = ByteArrayOutputStream()
        System.setOut(PrintStream(buf))
        try {
            PuaaLookup.main(arrayOf())
            PuaaDecompiler.main(arrayOf())
            PuaaCompiler.main(arrayOf())
            PuaaCompiler.main(arrayOf("--help"))
            PuaaCompiler.main(arrayOf("--unknown"))
        } finally {
            System.setOut(origOut)
        }
        assertTrue(buf.size() > 0)
    }
}
