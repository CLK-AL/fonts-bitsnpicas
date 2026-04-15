package com.kreative.bitsnpicas.coverage.truetype

import com.kreative.bitsnpicas.truetype.MaxpTable
import com.kreative.bitsnpicas.truetype.SbixEntry
import com.kreative.bitsnpicas.truetype.SbixSubtable
import com.kreative.bitsnpicas.truetype.SbixTable
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Round-trip the sbix table (Apple Color Bitmap). SbixTable depends on
 * MaxpTable.numGlyphs. Each subtable holds one entry per glyph; entries can
 * be empty. SbixEntry round-trip is also exercised.
 */
class SbixTablesTest {

    @Test
    fun sbixEntry_round_trip_with_image_payload() {
        val e = SbixEntry()
        e.offsetX = 3
        e.offsetY = -4
        e.imageType = SbixEntry.IMAGE_TYPE_PNG
        e.imageData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val bytes = e.compile()
        // 2 + 2 + 4 + 4 = 12.
        assertEquals(12, bytes.size)

        val e2 = SbixEntry()
        e2.decompile(bytes)
        assertEquals(3, e2.offsetX)
        assertEquals(-4, e2.offsetY)
        assertEquals(SbixEntry.IMAGE_TYPE_PNG, e2.imageType)
        assertEquals("png ", e2.imageTypeString)
        assertTrue(e2.imageData.contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
    }

    @Test
    fun sbixEntry_setImageTypeString_packs_chars() {
        val e = SbixEntry()
        e.setImageTypeString("png ")
        assertEquals(SbixEntry.IMAGE_TYPE_PNG, e.imageType)
        e.setImageTypeString("dupe")
        assertEquals(SbixEntry.IMAGE_TYPE_DUPE, e.imageType)
        // Out-of-range chars get replaced with space.
        e.setImageTypeString("\u0001\u0002\u0003\u0004")
        // All four chars are below 0x20, so they all become 0x20.
        assertEquals(0x20202020, e.imageType)
    }

    @Test
    fun sbixEntry_empty_data_decompiles_to_zero_payload() {
        val e = SbixEntry()
        e.decompile(byteArrayOf())
        assertEquals(0, e.offsetX)
        assertEquals(0, e.imageType)
        assertEquals(0, e.imageData.size)
        // Compiling an empty entry produces empty bytes (early-return branch).
        assertEquals(0, e.compile().size)
    }

    @Test
    fun sbixTable_round_trip_with_one_subtable_one_glyph() {
        val maxp = MaxpTable(); maxp.numGlyphs = 1

        val sbix = SbixTable()
        val sub = SbixSubtable()
        sub.ppem = 32; sub.dpi = 72
        val entry = SbixEntry()
        entry.imageType = SbixEntry.IMAGE_TYPE_PNG
        entry.imageData = byteArrayOf(1, 2, 3, 4)
        sub.add(entry)
        sbix.add(sub)

        val bytes = sbix.compile(arrayOf(maxp))
        val sbix2 = SbixTable()
        sbix2.decompile(bytes, arrayOf(maxp))
        assertEquals(1, sbix2.size)
        assertEquals(32, sbix2[0].ppem)
        assertEquals(72, sbix2[0].dpi)
        assertEquals(1, sbix2[0].size)
        assertEquals(SbixEntry.IMAGE_TYPE_PNG, sbix2[0][0].imageType)
        assertTrue(sbix2[0][0].imageData.contentEquals(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun sbixTable_basic_metadata() {
        val sbix = SbixTable()
        assertEquals("sbix", sbix.tableName())
        assertTrue(sbix.dependencyNames().contentEquals(arrayOf("maxp")))
        assertEquals(SbixTable.VERSION_DEFAULT, sbix.version)
        assertEquals(SbixTable.FLAGS_DEFAULT, sbix.flags)
    }
}
