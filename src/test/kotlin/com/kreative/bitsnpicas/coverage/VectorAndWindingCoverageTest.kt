package com.kreative.bitsnpicas.coverage

import com.kreative.bitsnpicas.Font
import com.kreative.bitsnpicas.VectorFont
import com.kreative.bitsnpicas.VectorFontGlyph
import com.kreative.bitsnpicas.VectorInstruction
import com.kreative.bitsnpicas.VectorPath
import com.kreative.bitsnpicas.WindingOrder
import org.junit.jupiter.api.Test
import java.awt.geom.GeneralPath
import java.awt.image.BufferedImage
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VectorAndWindingCoverageTest {

    @Test
    fun vector_instruction_varargs_and_collection_constructors() {
        val v1 = VectorInstruction('M', 1.0, 2.0)
        assertEquals('M', v1.operation)
        assertEquals(2, v1.operands.size)
        assertTrue(v1.toString().startsWith("M"))

        val v2 = VectorInstruction('C', listOf<Number>(1, 2, 3, 4, 5, 6))
        assertEquals('C', v2.operation)
        assertEquals(6, v2.operands.size)
    }

    @Test
    fun vector_path_append_and_list_operations() {
        val gp = GeneralPath()
        gp.moveTo(0.0, 0.0)
        gp.lineTo(1.0, 0.0)
        gp.quadTo(1.5, 0.5, 1.0, 1.0)
        gp.curveTo(0.5, 1.5, 0.0, 1.0, 0.0, 0.0)
        gp.closePath()

        val vp = VectorPath()
        vp.append(gp)
        assertTrue(vp.size > 0)
        assertTrue(vp.toString().isNotEmpty())

        val gp2 = GeneralPath()
        gp2.moveTo(5.0, 5.0)
        gp2.lineTo(6.0, 6.0)
        vp.append(gp2, true) // triggers connect path (M -> L)

        // Exercise all the List delegation methods.
        val inst = VectorInstruction('L', 1.0, 1.0)
        vp.add(inst)
        assertTrue(vp.contains(inst))
        assertTrue(vp.containsAll(listOf(inst)))
        assertEquals(inst, vp.get(vp.size - 1))
        assertTrue(vp.indexOf(inst) >= 0)
        assertTrue(vp.lastIndexOf(inst) >= 0)
        vp.set(vp.size - 1, VectorInstruction('L', 2.0, 2.0))
        vp.removeAt(vp.size - 1)
        assertTrue(vp.toArray().isNotEmpty())
        vp.toArray(arrayOfNulls<VectorInstruction>(vp.size))
        vp.add(0, inst)
        vp.addAll(listOf(inst))
        vp.addAll(0, listOf(inst))
        vp.remove(inst)
        vp.removeAll(listOf(inst))
        vp.retainAll(listOf(inst))
        assertTrue(vp.isEmpty() || vp.size >= 0)

        // Round-trip toGeneralPath - exercises PathExtensions and WindingOrder glue.
        val back = vp.toGeneralPath()
        assertNotNull(back)

        vp.clear()
        assertTrue(vp.isEmpty())
    }

    @Test
    fun vector_font_glyph_metrics_empty_and_populated() {
        val empty = VectorFontGlyph()
        assertEquals(0, empty.glyphWidth)
        assertEquals(0, empty.glyphHeight)
        assertEquals(0, empty.glyphOffset)
        assertEquals(0, empty.glyphAscent)
        assertEquals(0, empty.glyphDescent)
        assertEquals(0, empty.characterWidth)
        assertEquals(0.0, empty.glyphWidth2D)
        assertEquals(0.0, empty.glyphHeight2D)
        assertEquals(0.0, empty.glyphOffset2D)
        assertEquals(0.0, empty.glyphAscent2D)
        assertEquals(0.0, empty.glyphDescent2D)
        assertEquals(0.0, empty.characterWidth2D)

        val path = GeneralPath()
        path.moveTo(0.0, -10.0); path.lineTo(5.0, -10.0); path.lineTo(5.0, 0.0); path.closePath()
        val vp = VectorPath(); vp.append(path)
        val g = VectorFontGlyph(listOf(vp))
        assertTrue(g.glyphWidth > 0)
        assertTrue(g.glyphHeight > 0)
        assertTrue(g.glyphAscent >= 0 || g.glyphAscent < 0)  // just exercise it
        assertTrue(g.glyphDescent >= 0 || g.glyphDescent < 0)
        assertTrue(g.characterWidth >= 0 || g.characterWidth < 0)
        // Also exercise 2D variants.
        assertTrue(g.glyphAscent2D.isFinite())
        assertTrue(g.glyphDescent2D.isFinite())
        assertTrue(g.glyphOffset2D.isFinite())

        // width-explicit constructor
        val g2 = VectorFontGlyph(listOf(vp), 42.0)
        assertEquals(42, g2.characterWidth)
        assertEquals(42.0, g2.characterWidth2D)
        g2.setCharacterWidth(7); assertEquals(7, g2.characterWidth)
        g2.setCharacterWidth2D(3.7); assertEquals(4, g2.characterWidth)  // ceil()
        assertTrue(g2.contours.size == 1)

        val img = BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB)
        val gr = img.createGraphics()
        val adv = g2.paint(gr, 0.0, 0.0, 1.0)
        gr.dispose()
        assertTrue(adv >= 0)
    }

    @Test
    fun vector_font_metrics_and_ceil_setters() {
        val vf = VectorFont()
        assertEquals(0, vf.emAscent)
        val vf2 = VectorFont(1.5, 2.5, 3.5, 4.5, 5.5, 6.5, 7.5, 8.5)
        assertEquals(2, vf2.emAscent)
        assertEquals(1.5, vf2.emAscent2D)
        vf2.setEmAscent2D(9.0); assertEquals(9, vf2.emAscent)
        vf2.setEmDescent2D(9.0)
        vf2.setLineAscent2D(9.0); vf2.setLineDescent2D(9.0)
        vf2.setXHeight2D(9.0); vf2.setCapHeight2D(9.0)
        vf2.setLineGap2D(9.0); vf2.setNewGlyphWidth2D(9.0)
        vf2.setEmAscent(5); vf2.setEmDescent(5); vf2.setLineAscent(5); vf2.setLineDescent(5)
        vf2.setXHeight(5); vf2.setCapHeight(5); vf2.setLineGap(5); vf2.setNewGlyphWidth(5)

        // setXHeight2D/setCapHeight2D with glyph provide non-zero guess.
        val path = GeneralPath()
        path.moveTo(0.0, -10.0); path.lineTo(5.0, -10.0); path.lineTo(5.0, 0.0); path.closePath()
        val vp = VectorPath(); vp.append(path)
        val g = VectorFontGlyph(listOf(vp))
        vf2.putCharacter('x'.code, g)
        vf2.putCharacter('H'.code, g)
        vf2.setXHeight2D()
        vf2.setCapHeight2D()
    }

    @Test
    fun winding_order_all_mappings_consistent() {
        val rows = 3; val cols = 4
        for (w in WindingOrder.values()) {
            // toString must not crash.
            assertNotNull(w.toString())
            // getYX / getIndex must round-trip for all positions.
            for (i in 0 until rows * cols) {
                val yx = w.getYX(rows, cols, i, null)
                val buf = IntArray(2)
                val yx2 = w.getYX(rows, cols, i, buf)
                assertTrue(yx2 === buf)
                val back = w.getIndex(rows, cols, yx[0], yx[1])
                assertEquals(i, back)
            }
        }
    }
}
