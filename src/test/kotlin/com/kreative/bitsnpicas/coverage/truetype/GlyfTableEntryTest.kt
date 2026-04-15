package com.kreative.bitsnpicas.coverage.truetype

import com.kreative.bitsnpicas.truetype.GlyfTableEntry
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.test.assertEquals

/**
 * Drives the GlyfTableEntry compile/decompile branches. The class is marked
 * "BROKEN" in source comments and is not used end-to-end by the bitmap-font
 * exporter pipeline (which writes empty glyf entries via TTFBitmapFontExporter),
 * but the parser/serialiser still has 200+ uncovered lines we can lift.
 *
 * We focus on the basic single-contour simple glyph and a single-component
 * compound glyph - the structural variants. Edge transforms (xyScale, two-by-two)
 * and short-vector encodings would each need their own fixture.
 */
class GlyfTableEntryTest {

    private fun bytesOf(e: GlyfTableEntry): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { e.compile(it) }
        return out.toByteArray()
    }

    private fun parse(bytes: ByteArray): GlyfTableEntry {
        val e = GlyfTableEntry()
        DataInputStream(ByteArrayInputStream(bytes)).use { e.decompile(it) }
        return e
    }

    @Test
    fun simpleGlyph_one_contour_no_short_vectors() {
        val e = GlyfTableEntry()
        e.numberOfContours = 1
        e.xMin = 0; e.yMin = 0; e.xMax = 10; e.yMax = 10
        e.endPointsOfContours = intArrayOf(2) // 3 points, contour ends at index 2
        e.instructions = intArrayOf()
        // Use plain 2-byte coords (no SHORT/SAME flags).
        e.flags = intArrayOf(GlyfTableEntry.FLAG_ON_CURVE, GlyfTableEntry.FLAG_ON_CURVE, GlyfTableEntry.FLAG_ON_CURVE)
        e.xCoordinates = intArrayOf(0, 5, 10)
        e.yCoordinates = intArrayOf(0, 10, 0)

        val bytes = bytesOf(e)
        val parsed = parse(bytes)
        assertEquals(1, parsed.numberOfContours)
        assertEquals(2, parsed.endPointsOfContours[0])
        assertEquals(0, parsed.instructions.size)
        assertEquals(3, parsed.xCoordinates.size)
        assertEquals(0, parsed.xCoordinates[0])
        assertEquals(5, parsed.xCoordinates[1])
        assertEquals(10, parsed.xCoordinates[2])
        assertEquals(0, parsed.yCoordinates[0])
        assertEquals(10, parsed.yCoordinates[1])
        assertEquals(0, parsed.yCoordinates[2])
    }

    @Test
    fun simpleGlyph_with_THIS_X_IS_SAME_and_THIS_Y_IS_SAME_flags() {
        // Three points that are all at the same X and same Y - exercises the
        // FLAG_THIS_X_IS_SAME / FLAG_THIS_Y_IS_SAME branches in compile/decompile.
        val e = GlyfTableEntry()
        e.numberOfContours = 1
        e.xMin = 0; e.yMin = 0; e.xMax = 5; e.yMax = 5
        e.endPointsOfContours = intArrayOf(2)
        e.instructions = intArrayOf()
        // Set both same-X and same-Y flags. Pre-load lastX/lastY by writing
        // the first coordinate as a delta (no flag), then assert later coords
        // round-trip to the same value.
        val sameFlags = GlyfTableEntry.FLAG_ON_CURVE or
                GlyfTableEntry.FLAG_THIS_X_IS_SAME or
                GlyfTableEntry.FLAG_THIS_Y_IS_SAME
        e.flags = intArrayOf(GlyfTableEntry.FLAG_ON_CURVE, sameFlags, sameFlags)
        e.xCoordinates = intArrayOf(3, 3, 3)
        e.yCoordinates = intArrayOf(4, 4, 4)
        val bytes = bytesOf(e)
        val parsed = parse(bytes)
        assertEquals(3, parsed.xCoordinates[0])
        assertEquals(3, parsed.xCoordinates[1])
        assertEquals(3, parsed.xCoordinates[2])
        assertEquals(4, parsed.yCoordinates[0])
        assertEquals(4, parsed.yCoordinates[1])
        assertEquals(4, parsed.yCoordinates[2])
    }

    // (numberOfContours == 0 is buggy in the legacy compile path - it tries to
    // read flags[0] regardless of contour count - so we don't exercise it here.)

    @Test
    fun simpleGlyph_with_instructions() {
        val e = GlyfTableEntry()
        e.numberOfContours = 1
        e.xMin = 0; e.yMin = 0; e.xMax = 1; e.yMax = 1
        e.endPointsOfContours = intArrayOf(0)
        e.instructions = intArrayOf(0x12, 0x34, 0x56)
        e.flags = intArrayOf(GlyfTableEntry.FLAG_ON_CURVE)
        e.xCoordinates = intArrayOf(1)
        e.yCoordinates = intArrayOf(1)
        val parsed = parse(bytesOf(e))
        assertEquals(3, parsed.instructions.size)
        assertEquals(0x12, parsed.instructions[0])
        assertEquals(0x56, parsed.instructions[2])
    }

    @Test
    fun compoundGlyph_one_component_no_transform_byte_args() {
        val e = GlyfTableEntry()
        e.numberOfContours = -1
        e.xMin = 0; e.yMin = 0; e.xMax = 5; e.yMax = 5
        e.numberOfComponents = 1
        // Single component - no MORE_COMPONENTS, byte args, signed.
        e.componentFlags = intArrayOf(GlyfTableEntry.COMPONENT_FLAG_ARGS_ARE_XY_VALUES)
        e.componentGlyphIndex = intArrayOf(7)
        e.componentArgument1 = intArrayOf(1)
        e.componentArgument2 = intArrayOf(-2)
        e.componentTransformA = doubleArrayOf(1.0)
        e.componentTransformB = doubleArrayOf(0.0)
        e.componentTransformC = doubleArrayOf(0.0)
        e.componentTransformD = doubleArrayOf(1.0)

        val parsed = parse(bytesOf(e))
        assertEquals(-1, parsed.numberOfContours)
        assertEquals(1, parsed.numberOfComponents)
        assertEquals(7, parsed.componentGlyphIndex[0])
        assertEquals(1, parsed.componentArgument1[0])
        assertEquals(-2, parsed.componentArgument2[0])
    }

    @Test
    fun compoundGlyph_word_args_unsigned() {
        val e = GlyfTableEntry()
        e.numberOfContours = -1
        e.numberOfComponents = 1
        // Word args, NOT signed (XY_VALUES off) - exercises the unsigned-word branch.
        e.componentFlags = intArrayOf(GlyfTableEntry.COMPONENT_FLAG_ARG_1_AND_2_ARE_WORDS)
        e.componentGlyphIndex = intArrayOf(0x1234)
        e.componentArgument1 = intArrayOf(0x4000)
        e.componentArgument2 = intArrayOf(0x5000)
        e.componentTransformA = doubleArrayOf(1.0)
        e.componentTransformB = doubleArrayOf(0.0)
        e.componentTransformC = doubleArrayOf(0.0)
        e.componentTransformD = doubleArrayOf(1.0)

        val parsed = parse(bytesOf(e))
        assertEquals(0x1234, parsed.componentGlyphIndex[0])
        assertEquals(0x4000, parsed.componentArgument1[0])
        assertEquals(0x5000, parsed.componentArgument2[0])
    }

    @Test
    fun compoundGlyph_byte_args_unsigned() {
        // Byte args, unsigned (XY_VALUES off).
        val e = GlyfTableEntry()
        e.numberOfContours = -1
        e.numberOfComponents = 1
        e.componentFlags = intArrayOf(0)
        e.componentGlyphIndex = intArrayOf(3)
        e.componentArgument1 = intArrayOf(0xA0)
        e.componentArgument2 = intArrayOf(0xB0)
        e.componentTransformA = doubleArrayOf(1.0)
        e.componentTransformB = doubleArrayOf(0.0)
        e.componentTransformC = doubleArrayOf(0.0)
        e.componentTransformD = doubleArrayOf(1.0)
        val parsed = parse(bytesOf(e))
        assertEquals(0xA0, parsed.componentArgument1[0])
        assertEquals(0xB0, parsed.componentArgument2[0])
    }
}
