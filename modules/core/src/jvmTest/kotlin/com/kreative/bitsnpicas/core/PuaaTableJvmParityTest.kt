package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.puaa.PuaaEntry
import com.kreative.bitsnpicas.core.puaa.PuaaSubtable
import com.kreative.bitsnpicas.core.puaa.PuaaTable
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * JVM parity test for the PUAA table binary format.
 *
 * Compiles PuaaTable instances via both paths:
 *   1. commonMain [PuaaTable.compile]
 *   2. frozen Java [JavaLegacyAdapter.compilePuaaViaJava]
 * and asserts byte-exact equality.
 *
 * Also tests decompile parity: decompile the same bytes via both
 * paths and assert semantic equality.
 */
class PuaaTableJvmParityTest {

    private fun assertCompileParity(table: PuaaTable) {
        val kotlinBytes = table.compile()
        val javaBytes = JavaLegacyAdapter.compilePuaaViaJava(table)
        assertEquals(
            javaBytes.toList(),
            kotlinBytes.toList(),
            "Byte-exact parity failed: kotlin=${kotlinBytes.size} bytes, java=${javaBytes.size} bytes",
        )
    }

    private fun assertDecompileParity(table: PuaaTable) {
        val bytes = table.compile()
        val kotlinResult = PuaaTable.decompile(bytes)
        val javaResult = JavaLegacyAdapter.decompilePuaaViaJava(bytes)

        assertEquals(kotlinResult.version, javaResult.version, "version mismatch")
        assertEquals(kotlinResult.subtables.size, javaResult.subtables.size, "subtable count mismatch")
        for (i in kotlinResult.subtables.indices) {
            val ks = kotlinResult.subtables[i]
            val js = javaResult.subtables[i]
            assertEquals(ks.property, js.property, "property mismatch at subtable $i")
            assertEquals(ks.entries.size, js.entries.size, "entry count mismatch for ${ks.property}")
            for (j in ks.entries.indices) {
                val ke = ks.entries[j]
                val je = js.entries[j]
                assertEquals(ke.firstCodePoint, je.firstCodePoint, "firstCP mismatch at ${ks.property}[$j]")
                assertEquals(ke.lastCodePoint, je.lastCodePoint, "lastCP mismatch at ${ks.property}[$j]")
                // Compare property values across the range.
                for (cp in ke.firstCodePoint..ke.lastCodePoint) {
                    assertEquals(
                        ke.getPropertyValue(cp),
                        je.getPropertyValue(cp),
                        "value mismatch at ${ks.property}[$j] cp=0x${cp.toString(16)}",
                    )
                }
            }
        }
    }

    // ---- Single entry parity -----------------------------------------------

    @Test
    fun `Single entry compile parity`() {
        assertCompileParity(PuaaTable(subtables = listOf(
            PuaaSubtable("General_Category", listOf(
                PuaaEntry.Single(0x0041, 0x005A, "Lu"),
                PuaaEntry.Single(0x0061, 0x007A, "Ll"),
            )),
        )))
    }

    @Test
    fun `Single entry with long string compile parity`() {
        assertCompileParity(PuaaTable(subtables = listOf(
            PuaaSubtable("Name", listOf(
                PuaaEntry.Single(0x01FA, 0x01FA, "LATIN CAPITAL LETTER A WITH RING ABOVE AND ACUTE"),
            )),
        )))
    }

    // ---- Multiple entry parity ---------------------------------------------

    @Test
    fun `Multiple entry compile parity`() {
        assertCompileParity(PuaaTable(subtables = listOf(
            PuaaSubtable("Script", listOf(
                PuaaEntry.Multiple(0x0041, 0x0043, listOf("Latin", "Latin", "Latin")),
            )),
        )))
    }

    // ---- Boolean entry parity ----------------------------------------------

    @Test
    fun `Boolean entry compile parity`() {
        assertCompileParity(PuaaTable(subtables = listOf(
            PuaaSubtable("Alphabetic", listOf(
                PuaaEntry.BooleanEntry(0x0041, 0x005A, true),
                PuaaEntry.BooleanEntry(0x0030, 0x0039, false),
            )),
        )))
    }

    // ---- Decimal entry parity ----------------------------------------------

    @Test
    fun `Decimal entry compile parity`() {
        assertCompileParity(PuaaTable(subtables = listOf(
            PuaaSubtable("Numeric_Value", listOf(
                PuaaEntry.Decimal(0x0030, 0x0030, 0),
                PuaaEntry.Decimal(0x0031, 0x0031, 1),
                PuaaEntry.Decimal(0x0039, 0x0039, 9),
            )),
        )))
    }

    // ---- Hexadecimal entry parity ------------------------------------------

    @Test
    fun `Hexadecimal entry compile parity`() {
        assertCompileParity(PuaaTable(subtables = listOf(
            PuaaSubtable("Simple_Lowercase_Mapping", listOf(
                PuaaEntry.Hexadecimal(0x0041, 0x0041, 0x0061),
            )),
        )))
    }

    // ---- HexMultiple entry parity ------------------------------------------

    @Test
    fun `HexMultiple entry compile parity`() {
        assertCompileParity(PuaaTable(subtables = listOf(
            PuaaSubtable("Bidi_Mirroring_Glyph", listOf(
                PuaaEntry.HexMultiple(0x0028, 0x0029, intArrayOf(0x0029, 0x0028)),
            )),
        )))
    }

    // ---- HexSequence entry parity ------------------------------------------

    @Test
    fun `HexSequence entry compile parity`() {
        assertCompileParity(PuaaTable(subtables = listOf(
            PuaaSubtable("Decomposition_Mapping", listOf(
                PuaaEntry.HexSequence(0x00C0, 0x00C0, intArrayOf(0x0041, 0x0300)),
            )),
        )))
    }

    // ---- CaseMapping entry parity ------------------------------------------

    @Test
    fun `CaseMapping entry compile parity`() {
        assertCompileParity(PuaaTable(subtables = listOf(
            PuaaSubtable("Special_Casing", listOf(
                PuaaEntry.CaseMapping(0x00DF, 0x00DF, intArrayOf(0x0053, 0x0053), null),
            )),
        )))
    }

    @Test
    fun `CaseMapping entry with condition compile parity`() {
        assertCompileParity(PuaaTable(subtables = listOf(
            PuaaSubtable("Special_Casing", listOf(
                PuaaEntry.CaseMapping(0x0049, 0x0049, intArrayOf(0x0069, 0x0307), "lt"),
            )),
        )))
    }

    // ---- NameAlias entry parity --------------------------------------------

    @Test
    fun `NameAlias entry compile parity`() {
        assertCompileParity(PuaaTable(subtables = listOf(
            PuaaSubtable("Name_Alias", listOf(
                PuaaEntry.NameAlias(0x0000, 0x0000, "NULL", "control"),
                PuaaEntry.NameAlias(0x0001, 0x0001, "START OF HEADING", "control"),
            )),
        )))
    }

    // ---- Multi-subtable parity ---------------------------------------------

    @Test
    fun `multiple subtables compile parity`() {
        assertCompileParity(PuaaTable(subtables = listOf(
            PuaaSubtable("Z_last", listOf(PuaaEntry.BooleanEntry(0x0041, 0x0041, true))),
            PuaaSubtable("A_first", listOf(PuaaEntry.Decimal(0x0030, 0x0030, 42))),
        )))
    }

    // ---- Supplementary code points parity ----------------------------------

    @Test
    fun `supplementary code points compile parity`() {
        assertCompileParity(PuaaTable(subtables = listOf(
            PuaaSubtable("General_Category", listOf(
                PuaaEntry.Single(0x10000, 0x1000F, "Lo"),
                PuaaEntry.Single(0x1F600, 0x1F64F, "So"),
            )),
        )))
    }

    // ---- Comprehensive mixed test ------------------------------------------

    @Test
    fun `comprehensive all-types compile parity`() {
        assertCompileParity(PuaaTable(subtables = listOf(
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
        )))
    }

    // ---- Decompile parity --------------------------------------------------

    @Test
    fun `comprehensive decompile parity`() {
        assertDecompileParity(PuaaTable(subtables = listOf(
            PuaaSubtable("General_Category", listOf(
                PuaaEntry.Single(0x0041, 0x005A, "Lu"),
                PuaaEntry.Multiple(0x00C0, 0x00C2, listOf("Lu", "Lu", "Lu")),
            )),
            PuaaSubtable("Alphabetic", listOf(
                PuaaEntry.BooleanEntry(0x0041, 0x005A, true),
            )),
            PuaaSubtable("Numeric_Value", listOf(
                PuaaEntry.Decimal(0x0030, 0x0030, 0),
            )),
            PuaaSubtable("Mapping", listOf(
                PuaaEntry.Hexadecimal(0x0041, 0x0041, 0x0061),
                PuaaEntry.HexMultiple(0x0028, 0x0029, intArrayOf(0x0029, 0x0028)),
                PuaaEntry.HexSequence(0x00C0, 0x00C0, intArrayOf(0x0041, 0x0300)),
            )),
            PuaaSubtable("Special_Casing", listOf(
                PuaaEntry.CaseMapping(0x00DF, 0x00DF, intArrayOf(0x0053, 0x0053), "de"),
            )),
            PuaaSubtable("Name_Alias", listOf(
                PuaaEntry.NameAlias(0x0000, 0x0000, "NULL", "control"),
            )),
        )))
    }

    // ---- Null string parity ------------------------------------------------

    @Test
    fun `null string Single entry compile parity`() {
        assertCompileParity(PuaaTable(subtables = listOf(
            PuaaSubtable("test", listOf(
                PuaaEntry.Single(0x0041, 0x0041, null),
            )),
        )))
    }

    // ---- Cross-compile decompile parity ------------------------------------

    @Test
    fun `kotlin compile then java decompile yields same data`() {
        val table = PuaaTable(subtables = listOf(
            PuaaSubtable("gc", listOf(
                PuaaEntry.Single(0x0041, 0x005A, "Lu"),
            )),
        ))
        val kotlinBytes = table.compile()
        val javaResult = JavaLegacyAdapter.decompilePuaaViaJava(kotlinBytes)
        assertEquals(1, javaResult.subtables.size)
        assertEquals("gc", javaResult.subtables[0].property)
        assertEquals("Lu", javaResult.subtables[0].entries[0].getPropertyValue(0x0041))
    }

    @Test
    fun `java compile then kotlin decompile yields same data`() {
        val table = PuaaTable(subtables = listOf(
            PuaaSubtable("gc", listOf(
                PuaaEntry.Single(0x0041, 0x005A, "Lu"),
            )),
        ))
        val javaBytes = JavaLegacyAdapter.compilePuaaViaJava(table)
        val kotlinResult = PuaaTable.decompile(javaBytes)
        assertEquals(1, kotlinResult.subtables.size)
        assertEquals("gc", kotlinResult.subtables[0].property)
        assertEquals("Lu", kotlinResult.subtables[0].entries[0].getPropertyValue(0x0041))
    }
}
