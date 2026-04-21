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

    // ---- EastAsianWidthCodec ------------------------------------------------

    @Test
    fun `EastAsianWidthCodec round-trips`() {
        val input = listOf(
            "0020;Na",
            "3000;F",
            "4E00..9FFF;W",
        )
        val codec = EastAsianWidthCodec()
        val table = compileCodec(codec, input)
        val st = table.subtables.firstOrNull { it.property == "East_Asian_Width" }
        assertNotNull(st)
        assertEquals("Na", st.getPropertyValue(0x0020))
        assertEquals("W", st.getPropertyValue(0x4E00))
        val output = codec.decompile(table)
        assertTrue(output.isNotEmpty())
        val table2 = compileCodec(codec, output)
        assertEquals("Na", table2.subtables.first { it.property == "East_Asian_Width" }.getPropertyValue(0x0020))
    }

    // ---- LineBreakCodec ----------------------------------------------------

    @Test
    fun `LineBreakCodec round-trips`() {
        val input = listOf(
            "0009;BA",
            "000A;LF",
            "0041..005A;AL",
        )
        val codec = LineBreakCodec()
        val table = compileCodec(codec, input)
        val st = table.subtables.firstOrNull { it.property == "Line_Break" }
        assertNotNull(st)
        assertEquals("BA", st.getPropertyValue(0x0009))
        assertEquals("AL", st.getPropertyValue(0x0041))
        val output = codec.decompile(table)
        assertTrue(output.isNotEmpty())
        val table2 = compileCodec(codec, output)
        assertEquals("AL", table2.subtables.first { it.property == "Line_Break" }.getPropertyValue(0x0050))
    }

    // ---- VerticalOrientationCodec ------------------------------------------

    @Test
    fun `VerticalOrientationCodec round-trips`() {
        val input = listOf(
            "0000..007F    ; R",
            "00A2..00A3    ; U",
        )
        val codec = VerticalOrientationCodec()
        val table = compileCodec(codec, input)
        val st = table.subtables.firstOrNull { it.property == "Vertical_Orientation" }
        assertNotNull(st)
        assertEquals("R", st.getPropertyValue(0x0041))
        assertEquals("U", st.getPropertyValue(0x00A2))
        val output = codec.decompile(table)
        assertTrue(output.isNotEmpty())
    }

    // ---- GraphemeBreakPropertyCodec ----------------------------------------

    @Test
    fun `GraphemeBreakPropertyCodec round-trips`() {
        val input = listOf(
            "000D          ; CR",
            "000A          ; LF",
            "0000..0009    ; Control",
        )
        val codec = GraphemeBreakPropertyCodec()
        val table = compileCodec(codec, input)
        val st = table.subtables.firstOrNull { it.property == "Grapheme_Cluster_Break" }
        assertNotNull(st)
        assertEquals("CR", st.getPropertyValue(0x000D))
        assertEquals("LF", st.getPropertyValue(0x000A))
        assertEquals("Control", st.getPropertyValue(0x0001))
        val output = codec.decompile(table)
        assertTrue(output.isNotEmpty())
    }

    // ---- SentenceBreakPropertyCodec ----------------------------------------

    @Test
    fun `SentenceBreakPropertyCodec round-trips`() {
        val input = listOf(
            "000D          ; CR",
            "000A          ; LF",
            "0009          ; Sp",
        )
        val codec = SentenceBreakPropertyCodec()
        val table = compileCodec(codec, input)
        val st = table.subtables.firstOrNull { it.property == "Sentence_Break" }
        assertNotNull(st)
        assertEquals("CR", st.getPropertyValue(0x000D))
        val output = codec.decompile(table)
        assertTrue(output.isNotEmpty())
        val table2 = compileCodec(codec, output)
        assertEquals("CR", table2.subtables.first { it.property == "Sentence_Break" }.getPropertyValue(0x000D))
    }

    // ---- WordBreakPropertyCodec --------------------------------------------

    @Test
    fun `WordBreakPropertyCodec round-trips`() {
        val input = listOf(
            "000A          ; LF",
            "000D          ; CR",
            "0041..005A    ; ALetter",
        )
        val codec = WordBreakPropertyCodec()
        val table = compileCodec(codec, input)
        val st = table.subtables.firstOrNull { it.property == "Word_Break" }
        assertNotNull(st)
        assertEquals("ALetter", st.getPropertyValue(0x0041))
        val output = codec.decompile(table)
        assertTrue(output.isNotEmpty())
    }

    // ---- IndicPositionalCategoryCodec --------------------------------------

    @Test
    fun `IndicPositionalCategoryCodec round-trips`() {
        val input = listOf(
            "093C          ; Bottom",
            "0900..0902    ; Top",
        )
        val codec = IndicPositionalCategoryCodec()
        val table = compileCodec(codec, input)
        val st = table.subtables.firstOrNull { it.property == "Indic_Positional_Category" }
        assertNotNull(st)
        assertEquals("Bottom", st.getPropertyValue(0x093C))
        assertEquals("Top", st.getPropertyValue(0x0900))
        val output = codec.decompile(table)
        assertTrue(output.isNotEmpty())
    }

    // ---- IndicSyllabicCategoryCodec ----------------------------------------

    @Test
    fun `IndicSyllabicCategoryCodec round-trips`() {
        val input = listOf(
            "0900..0902    ; Bindu",
            "0903          ; Visarga",
        )
        val codec = IndicSyllabicCategoryCodec()
        val table = compileCodec(codec, input)
        val st = table.subtables.firstOrNull { it.property == "Indic_Syllabic_Category" }
        assertNotNull(st)
        assertEquals("Bindu", st.getPropertyValue(0x0900))
        assertEquals("Visarga", st.getPropertyValue(0x0903))
        val output = codec.decompile(table)
        assertTrue(output.isNotEmpty())
    }

    // ---- EmojiDataCodec ----------------------------------------------------

    @Test
    fun `EmojiDataCodec round-trips`() {
        val input = listOf(
            "0023          ; Emoji",
            "002A          ; Emoji",
            "0030..0039    ; Emoji",
            "00A9          ; Extended_Pictographic",
        )
        val codec = EmojiDataCodec()
        val table = compileCodec(codec, input)
        val emojiSt = table.subtables.firstOrNull { it.property == "Emoji" }
        assertNotNull(emojiSt)
        assertEquals("Y", emojiSt.getPropertyValue(0x0023))
        val output = codec.decompile(table)
        assertTrue(output.any { "Emoji" in it })
    }

    // ---- ArabicShapingCodec ------------------------------------------------

    @Test
    fun `ArabicShapingCodec round-trips`() {
        val input = listOf(
            "0600; ARABIC NUMBER SIGN; U; No_Joining_Group",
            "0621; HAMZA; U; No_Joining_Group",
            "0622; ALEF WITH MADDA ABOVE; R; ALEF",
        )
        val codec = ArabicShapingCodec()
        val table = compileCodec(codec, input)
        val typeSt = table.subtables.firstOrNull { it.property == "Joining_Type" }
        assertNotNull(typeSt)
        assertEquals("U", typeSt.getPropertyValue(0x0600))
        assertEquals("R", typeSt.getPropertyValue(0x0622))
        val output = codec.decompile(table)
        assertTrue(output.isNotEmpty())
    }

    // ---- BidiBracketsCodec -------------------------------------------------

    @Test
    fun `BidiBracketsCodec round-trips`() {
        val input = listOf(
            "0028; 0029; o",
            "0029; 0028; c",
            "005B; 005D; o",
        )
        val codec = BidiBracketsCodec()
        val table = compileCodec(codec, input)
        val bracketSt = table.subtables.firstOrNull { it.property == "Bidi_Paired_Bracket" }
        assertNotNull(bracketSt)
        assertEquals("0029", bracketSt.getPropertyValue(0x0028))
        val typeSt = table.subtables.firstOrNull { it.property == "Bidi_Paired_Bracket_Type" }
        assertNotNull(typeSt)
        assertEquals("o", typeSt.getPropertyValue(0x0028))
        val output = codec.decompile(table)
        assertEquals(3, output.size)
    }

    // ---- BidiMirroringCodec ------------------------------------------------

    @Test
    fun `BidiMirroringCodec round-trips`() {
        val input = listOf(
            "0028; 0029 # LEFT PARENTHESIS",
            "0029; 0028 # RIGHT PARENTHESIS",
        )
        val codec = BidiMirroringCodec()
        val table = compileCodec(codec, input)
        val st = table.subtables.firstOrNull { it.property == "Bidi_Mirroring_Glyph" }
        assertNotNull(st)
        assertEquals("0029", st.getPropertyValue(0x0028))
        assertEquals("0028", st.getPropertyValue(0x0029))
        val output = codec.decompile(table)
        assertEquals(2, output.size)
        val table2 = compileCodec(codec, output)
        assertEquals("0029", table2.subtables.first { it.property == "Bidi_Mirroring_Glyph" }.getPropertyValue(0x0028))
    }

    // ---- CompositionExclusionsCodec ----------------------------------------

    @Test
    fun `CompositionExclusionsCodec round-trips`() {
        val input = listOf(
            "0958    # DEVANAGARI LETTER QA",
            "0959    # DEVANAGARI LETTER KHHA",
            "095A    # DEVANAGARI LETTER GHHA",
        )
        val codec = CompositionExclusionsCodec()
        val table = compileCodec(codec, input)
        val st = table.subtables.firstOrNull { it.property == "Composition_Exclusion" }
        assertNotNull(st)
        assertEquals("Y", st.getPropertyValue(0x0958))
        val output = codec.decompile(table)
        assertEquals(3, output.size)
        assertTrue("0958" in output[0])
    }

    // ---- DerivedAgeCodec ---------------------------------------------------

    @Test
    fun `DerivedAgeCodec round-trips`() {
        val input = listOf(
            "0000..007F    ; 1.1",
            "0080..009F    ; 1.1",
            "00A0..00FF    ; 1.1",
            "0100..017F    ; 1.1",
            "0370..0373    ; 1.1",
            "1F600..1F64F  ; 6.0",
        )
        val codec = DerivedAgeCodec()
        val table = compileCodec(codec, input)
        val st = table.subtables.firstOrNull { it.property == "Age" }
        assertNotNull(st)
        assertEquals("1.1", st.getPropertyValue(0x0041))
        assertEquals("6.0", st.getPropertyValue(0x1F600))
        val output = codec.decompile(table)
        assertTrue(output.isNotEmpty())
    }

    // ---- EquivalentUnifiedIdeographCodec -----------------------------------

    @Test
    fun `EquivalentUnifiedIdeographCodec round-trips`() {
        val input = listOf(
            "2E81      ; 2E81",
            "2E82..2E83; 2E82",
        )
        val codec = EquivalentUnifiedIdeographCodec()
        val table = compileCodec(codec, input)
        val st = table.subtables.firstOrNull { it.property == "Equivalent_Unified_Ideograph" }
        assertNotNull(st)
        assertEquals("2E81", st.getPropertyValue(0x2E81))
        val output = codec.decompile(table)
        assertTrue(output.isNotEmpty())
    }

    // ---- HangulSyllableTypeCodec -------------------------------------------

    @Test
    fun `HangulSyllableTypeCodec round-trips`() {
        val input = listOf(
            "1100..1159    ; L",
            "1160..11A2    ; V",
            "11A8..11F9    ; T",
            "AC00          ; LV",
        )
        val codec = HangulSyllableTypeCodec()
        val table = compileCodec(codec, input)
        val st = table.subtables.firstOrNull { it.property == "Hangul_Syllable_Type" }
        assertNotNull(st)
        assertEquals("L", st.getPropertyValue(0x1100))
        assertEquals("V", st.getPropertyValue(0x1160))
        val output = codec.decompile(table)
        assertTrue(output.isNotEmpty())
    }

    // ---- JamoCodec ---------------------------------------------------------

    @Test
    fun `JamoCodec round-trips`() {
        val input = listOf(
            "1100; G",
            "1101; GG",
            "110B;",
        )
        val codec = JamoCodec()
        val table = compileCodec(codec, input)
        val st = table.subtables.firstOrNull { it.property == "Jamo_Short_Name" }
        assertNotNull(st)
        assertEquals("G", st.getPropertyValue(0x1100))
        assertEquals("GG", st.getPropertyValue(0x1101))
        assertEquals("", st.getPropertyValue(0x110B))
        val output = codec.decompile(table)
        assertEquals(3, output.size)
        val table2 = compileCodec(codec, output)
        assertEquals("G", table2.subtables.first { it.property == "Jamo_Short_Name" }.getPropertyValue(0x1100))
    }

    // ---- NameAliasesCodec --------------------------------------------------

    @Test
    fun `NameAliasesCodec round-trips`() {
        val input = listOf(
            "0000;NULL;control",
            "0000;NUL;abbreviation",
            "0001;START OF HEADING;control",
        )
        val codec = NameAliasesCodec()
        val table = compileCodec(codec, input)
        val st = table.subtables.firstOrNull { it.property == "Name_Alias" }
        assertNotNull(st)
        assertNotNull(st.getPropertyValue(0x0000))
        val output = codec.decompile(table)
        assertTrue(output.isNotEmpty())
    }

    // ---- ScriptExtensionsCodec ---------------------------------------------

    @Test
    fun `ScriptExtensionsCodec round-trips`() {
        val input = listOf(
            "0964          ; Beng Deva Gran Gujr",
            "0965          ; Beng Deva Gran Gujr",
            "0966..096F    ; Deva Dogr Kthi Mahj",
        )
        val codec = ScriptExtensionsCodec()
        val table = compileCodec(codec, input)
        val st = table.subtables.firstOrNull { it.property == "Script_Extensions" }
        assertNotNull(st)
        assertNotNull(st.getPropertyValue(0x0964))
        val output = codec.decompile(table)
        assertTrue(output.isNotEmpty())
    }

    // ---- SpecialCasingCodec ------------------------------------------------

    @Test
    fun `SpecialCasingCodec round-trips`() {
        val input = listOf(
            "00DF; 00DF; 0053 0073; 0053 0053; # LATIN SMALL LETTER SHARP S",
            "0130; 0069 0307; 0130; 0130; # LATIN CAPITAL LETTER I WITH DOT ABOVE",
        )
        val codec = SpecialCasingCodec()
        val table = compileCodec(codec, input)
        val lower = table.subtables.firstOrNull { it.property == "Lowercase_Mapping" }
        assertNotNull(lower)
        assertNotNull(lower.getPropertyValue(0x00DF))
        val output = codec.decompile(table)
        assertEquals(2, output.size)
    }

    // ---- UnihanCodec (NushuSourcesCodec) -----------------------------------

    @Test
    fun `NushuSourcesCodec round-trips`() {
        val input = listOf(
            "# NushuSources.txt",
            "U+1B170\tkSrc_NushuDuben\t001",
            "U+1B171\tkSrc_NushuDuben\t002",
            "U+1B171\tkReading\tnai",
        )
        val codec = NushuSourcesCodec()
        val table = compileCodec(codec, input)
        val srcSt = table.subtables.firstOrNull { it.property == "kSrc_NushuDuben" }
        assertNotNull(srcSt)
        assertNotNull(srcSt.getPropertyValue(0x1B170))
        val readSt = table.subtables.firstOrNull { it.property == "kReading" }
        assertNotNull(readSt)
        assertEquals("nai", readSt.getPropertyValue(0x1B171))
        val output = codec.decompile(table)
        assertTrue(output.isNotEmpty())
        val table2 = compileCodec(codec, output)
        assertEquals("nai", table2.subtables.first { it.property == "kReading" }.getPropertyValue(0x1B171))
    }

    // ---- TangutSourcesCodec ------------------------------------------------

    @Test
    fun `TangutSourcesCodec round-trips`() {
        val input = listOf(
            "U+17000\tkTGT_MergedSrc\tL2008-0001",
            "U+17001\tkRSTUnicode\t167.0",
        )
        val codec = TangutSourcesCodec()
        val table = compileCodec(codec, input)
        val st = table.subtables.firstOrNull { it.property == "kTGT_MergedSrc" }
        assertNotNull(st)
        val output = codec.decompile(table)
        assertTrue(output.isNotEmpty())
    }

    // ---- UnihanDictionaryIndicesCodec ---------------------------------------

    @Test
    fun `UnihanDictionaryIndicesCodec round-trips`() {
        val input = listOf(
            "U+3400\tkCowles\t1",
            "U+3401\tkCowles\t2",
        )
        val codec = UnihanDictionaryIndicesCodec()
        val table = compileCodec(codec, input)
        val st = table.subtables.firstOrNull { it.property == "kCowles" }
        assertNotNull(st)
        val output = codec.decompile(table)
        assertTrue(output.isNotEmpty())
    }

    // ---- UnihanVariantsCodec -----------------------------------------------

    @Test
    fun `UnihanVariantsCodec round-trips`() {
        val input = listOf(
            "U+4E0D\tkSimplifiedVariant\tU+4E0D",
            "U+4E0E\tkTraditionalVariant\tU+8207",
        )
        val codec = UnihanVariantsCodec()
        val table = compileCodec(codec, input)
        val st = table.subtables.firstOrNull { it.property == "kSimplifiedVariant" }
        assertNotNull(st)
        val output = codec.decompile(table)
        assertTrue(output.isNotEmpty())
        val table2 = compileCodec(codec, output)
        assertNotNull(table2.subtables.firstOrNull { it.property == "kSimplifiedVariant" })
    }

    // ---- PuaaCodecRegistry --------------------------------------------------

    @Test
    fun `PuaaCodecRegistry finds all registered codecs by filename`() {
        val registry = PuaaCodecRegistry.instance
        assertNotNull(registry.getCodec("Blocks.txt"))
        assertNotNull(registry.getCodec("PropList.txt"))
        assertNotNull(registry.getCodec("Scripts.txt"))
        assertNotNull(registry.getCodec("UnicodeData.txt"))
        assertNotNull(registry.getCodec("EastAsianWidth.txt"))
        assertNotNull(registry.getCodec("LineBreak.txt"))
        assertNotNull(registry.getCodec("VerticalOrientation.txt"))
        assertNotNull(registry.getCodec("GraphemeBreakProperty.txt"))
        assertNotNull(registry.getCodec("SentenceBreakProperty.txt"))
        assertNotNull(registry.getCodec("WordBreakProperty.txt"))
        assertNotNull(registry.getCodec("IndicPositionalCategory.txt"))
        assertNotNull(registry.getCodec("IndicSyllabicCategory.txt"))
        assertNotNull(registry.getCodec("emoji-data.txt"))
        assertNotNull(registry.getCodec("ArabicShaping.txt"))
        assertNotNull(registry.getCodec("BidiBrackets.txt"))
        assertNotNull(registry.getCodec("BidiMirroring.txt"))
        assertNotNull(registry.getCodec("CompositionExclusions.txt"))
        assertNotNull(registry.getCodec("DerivedAge.txt"))
        assertNotNull(registry.getCodec("EquivalentUnifiedIdeograph.txt"))
        assertNotNull(registry.getCodec("HangulSyllableType.txt"))
        assertNotNull(registry.getCodec("Jamo.txt"))
        assertNotNull(registry.getCodec("NameAliases.txt"))
        assertNotNull(registry.getCodec("ScriptExtensions.txt"))
        assertNotNull(registry.getCodec("SpecialCasing.txt"))
        assertNotNull(registry.getCodec("NushuSources.txt"))
        assertNotNull(registry.getCodec("TangutSources.txt"))
        assertNotNull(registry.getCodec("Unihan_DictionaryIndices.txt"))
        assertNotNull(registry.getCodec("Unihan_DictionaryLikeData.txt"))
        assertNotNull(registry.getCodec("Unihan_IRGSources.txt"))
        assertNotNull(registry.getCodec("Unihan_NumericValues.txt"))
        assertNotNull(registry.getCodec("Unihan_OtherMappings.txt"))
        assertNotNull(registry.getCodec("Unihan_RadicalStrokeCounts.txt"))
        assertNotNull(registry.getCodec("Unihan_Readings.txt"))
        assertNotNull(registry.getCodec("Unihan_Variants.txt"))
        // Case-insensitive lookup.
        assertNotNull(registry.getCodec("blocks.txt"))
        assertEquals(34, registry.getCodecs().size)
    }

    // ---- Helpers ------------------------------------------------------------

    private fun compileCodec(codec: PuaaCodec, lines: List<String>): PuaaTable {
        val mutable = MutablePuaaTable()
        codec.compile(mutable, lines.asSequence())
        return mutable.toTable()
    }
}
