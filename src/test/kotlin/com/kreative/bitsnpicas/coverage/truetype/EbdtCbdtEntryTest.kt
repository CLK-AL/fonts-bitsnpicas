package com.kreative.bitsnpicas.coverage.truetype

import com.kreative.bitsnpicas.truetype.CbdtEntryFormat17
import com.kreative.bitsnpicas.truetype.CbdtEntryFormat18
import com.kreative.bitsnpicas.truetype.CbdtEntryFormat19
import com.kreative.bitsnpicas.truetype.EbdtComponent
import com.kreative.bitsnpicas.truetype.EbdtEntry
import com.kreative.bitsnpicas.truetype.EbdtEntryFormat2
import com.kreative.bitsnpicas.truetype.EbdtEntryFormat5
import com.kreative.bitsnpicas.truetype.EbdtEntryFormat6
import com.kreative.bitsnpicas.truetype.EbdtEntryFormat7
import com.kreative.bitsnpicas.truetype.EbdtEntryFormat8
import com.kreative.bitsnpicas.truetype.EbdtEntryFormat9
import com.kreative.bitsnpicas.truetype.SbitBigGlyphMetrics
import com.kreative.bitsnpicas.truetype.SbitSmallGlyphMetrics
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Round-trip tests for EbdtEntryFormat 2/5/6/7/8/9 and CbdtEntryFormat
 * 17/18/19. The read/write methods are protected on EbdtEntry, so we use
 * reflection to invoke them with a known length / matched DataOutputStream.
 *
 * For each entry we:
 *   1. Construct an entry with synthetic image data and metrics.
 *   2. Serialise via write(DataOutputStream).
 *   3. Read the bytes back into a fresh entry via read(DataInputStream, length).
 *   4. Assert the metrics + image bytes match.
 */
class EbdtCbdtEntryTest {

    private fun smallMetrics(width: Int = 4, height: Int = 5): SbitSmallGlyphMetrics {
        val m = SbitSmallGlyphMetrics()
        m.height = height; m.width = width
        m.bearingX = 0; m.bearingY = height
        m.advance = width
        return m
    }

    private fun bigMetrics(width: Int = 4, height: Int = 5): SbitBigGlyphMetrics {
        val m = SbitBigGlyphMetrics()
        m.height = height; m.width = width
        m.horiBearingX = 0; m.horiBearingY = height; m.horiAdvance = width
        m.vertBearingX = -width / 2; m.vertBearingY = 0; m.vertAdvance = height
        return m
    }

    private fun roundTrip(entry: EbdtEntry): EbdtEntry {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use {
            val write = EbdtEntry::class.java.getDeclaredMethod("write", DataOutputStream::class.java)
            write.isAccessible = true
            write.invoke(entry, it)
            it.flush()
        }
        val bytes = bos.toByteArray()
        // Length check via length()
        val lengthMethod = EbdtEntry::class.java.getDeclaredMethod("length")
        lengthMethod.isAccessible = true
        val expectedLen = lengthMethod.invoke(entry) as Int
        assertEquals(expectedLen, bytes.size, "length() does not match bytes written")

        val out = entry.javaClass.getDeclaredConstructor().newInstance() as EbdtEntry
        DataInputStream(ByteArrayInputStream(bytes)).use {
            val read = EbdtEntry::class.java.getDeclaredMethod(
                "read", DataInputStream::class.java, Int::class.javaPrimitiveType
            )
            read.isAccessible = true
            read.invoke(out, it, bytes.size)
        }
        return out
    }

    @Test
    fun ebdtFormat2_round_trip() {
        val e = EbdtEntryFormat2()
        e.smallMetrics = smallMetrics()
        e.imageData = byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0x12, 0x34)
        val r = roundTrip(e) as EbdtEntryFormat2
        assertEquals(2, r.format())
        assertEquals(5, r.smallMetrics.height)
        assertTrue(r.imageData.contentEquals(e.imageData))
    }

    @Test
    fun ebdtFormat5_round_trip_no_metrics() {
        val e = EbdtEntryFormat5()
        e.imageData = byteArrayOf(1, 2, 3, 4, 5)
        val r = roundTrip(e) as EbdtEntryFormat5
        assertEquals(5, r.format())
        assertTrue(r.imageData.contentEquals(e.imageData))
    }

    @Test
    fun ebdtFormat6_round_trip_big_metrics() {
        val e = EbdtEntryFormat6()
        e.bigMetrics = bigMetrics()
        e.imageData = byteArrayOf(7, 8, 9)
        val r = roundTrip(e) as EbdtEntryFormat6
        assertEquals(6, r.format())
        assertEquals(4, r.bigMetrics.width)
        assertEquals(0, r.bigMetrics.horiBearingX)
        assertTrue(r.imageData.contentEquals(e.imageData))
    }

    @Test
    fun ebdtFormat7_round_trip_big_metrics() {
        val e = EbdtEntryFormat7()
        e.bigMetrics = bigMetrics(8, 8)
        e.imageData = byteArrayOf(0x33, 0x44, 0x55, 0x66, 0x77, 0x88.toByte())
        val r = roundTrip(e) as EbdtEntryFormat7
        assertEquals(7, r.format())
        assertEquals(8, r.bigMetrics.width)
        assertTrue(r.imageData.contentEquals(e.imageData))
    }

    @Test
    fun ebdtFormat8_round_trip_components() {
        val e = EbdtEntryFormat8()
        e.smallMetrics = smallMetrics()
        val c1 = EbdtComponent(); c1.glyphID = 7; c1.xOffset = 1; c1.yOffset = -2
        val c2 = EbdtComponent(); c2.glyphID = 11; c2.xOffset = 3; c2.yOffset = 4
        e.add(c1); e.add(c2)
        val r = roundTrip(e) as EbdtEntryFormat8
        assertEquals(8, r.format())
        assertEquals(2, r.size)
        assertEquals(7, r[0].glyphID)
        assertEquals(1, r[0].xOffset)
        assertEquals(-2, r[0].yOffset)
        assertEquals(11, r[1].glyphID)
    }

    @Test
    fun ebdtFormat9_round_trip_components_with_big_metrics() {
        val e = EbdtEntryFormat9()
        e.bigMetrics = bigMetrics()
        val c = EbdtComponent(); c.glyphID = 99; c.xOffset = 0; c.yOffset = 0
        e.add(c)
        val r = roundTrip(e) as EbdtEntryFormat9
        assertEquals(9, r.format())
        assertEquals(1, r.size)
        assertEquals(99, r[0].glyphID)
    }

    @Test
    fun cbdtFormat17_round_trip() {
        val e = CbdtEntryFormat17()
        e.glyphMetrics = smallMetrics()
        e.imageData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) // PNG header bytes
        val r = roundTrip(e) as CbdtEntryFormat17
        assertEquals(17, r.format())
        assertEquals(5, r.glyphMetrics.height)
        assertTrue(r.imageData.contentEquals(e.imageData))
    }

    @Test
    fun cbdtFormat18_round_trip() {
        val e = CbdtEntryFormat18()
        e.glyphMetrics = bigMetrics()
        e.imageData = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val r = roundTrip(e) as CbdtEntryFormat18
        assertEquals(18, r.format())
        assertEquals(4, r.glyphMetrics.width)
        assertTrue(r.imageData.contentEquals(e.imageData))
    }

    @Test
    fun cbdtFormat19_round_trip_no_metrics() {
        val e = CbdtEntryFormat19()
        e.imageData = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05)
        val r = roundTrip(e) as CbdtEntryFormat19
        assertEquals(19, r.format())
        assertTrue(r.imageData.contentEquals(e.imageData))
    }

    @Test
    fun ebdtFormat2_with_null_metrics_uses_default() {
        // Branch where smallMetrics is null - should write default zeroed metrics.
        val e = EbdtEntryFormat2()
        e.smallMetrics = null
        e.imageData = byteArrayOf(0x00)
        val r = roundTrip(e) as EbdtEntryFormat2
        assertEquals(0, r.smallMetrics.height)
        assertEquals(0, r.smallMetrics.width)
    }

    @Test
    fun ebdtFormat6_with_null_metrics_uses_default() {
        val e = EbdtEntryFormat6()
        e.bigMetrics = null
        e.imageData = byteArrayOf(0x00, 0x00)
        val r = roundTrip(e) as EbdtEntryFormat6
        assertEquals(0, r.bigMetrics.height)
        assertEquals(0, r.bigMetrics.horiAdvance)
    }
}
