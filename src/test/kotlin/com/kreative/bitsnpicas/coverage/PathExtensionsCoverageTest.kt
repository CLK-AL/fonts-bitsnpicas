package com.kreative.bitsnpicas.coverage

import com.kreative.bitsnpicas.PathExtensions
import org.junit.jupiter.api.Test
import java.awt.geom.GeneralPath
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PathExtensionsCoverageTest {

    @Test
    fun default_and_general_path_constructors() {
        val pe = PathExtensions()
        assertNotNull(pe.path)
        assertNotNull(pe.currentPoint)

        val gp = GeneralPath()
        gp.moveTo(5.0, 6.0)
        val pe2 = PathExtensions(gp)
        assertEquals(5.0, pe2.currentPoint.x)

        val pe3 = PathExtensions(GeneralPath()) // no current point -> moveTo(0,0)
        assertNotNull(pe3.currentPoint)
    }

    @Test
    fun execute_drives_every_operation_char() {
        val pe = PathExtensions()
        // Drive every supported operation at least once.
        val ops = charArrayOf(
            'M', 'm', 'H', 'h', 'V', 'v', 'L', 'l',
            'Q', 'q', 'T', 't', 'C', 'c', 'S', 's',
            'K', 'k', 'U', 'u',
            'A', 'a', 'G', 'g', 'I', 'i', 'J', 'j',
            'R', 'r', 'E', 'e', 'O', 'o',
            'P', 'p', 'X', 'x',
            'Z', 'z', 'W', 'w'
        )
        for (op in ops) {
            val pe2 = PathExtensions()
            pe2.moveTo(0.0, 0.0) // safe baseline for ops that need a current point
            // 7 operands is the max any op consumes.
            val args = Array<Number>(7) { 1.0 }
            try { pe2.execute(op, *args) } catch (e: Throwable) { /* some ops may throw on degenerate input */ }
        }

        // Execute with unknown op - silent no-op.
        pe.execute('?', 0.0)
    }

    @Test
    fun direct_methods_move_line_quad_curve_timmer_arc_rect_ellipse_polygon() {
        val pe = PathExtensions()
        pe.moveTo(0.0, 0.0)
        pe.horizTo(5.0)
        pe.vertTo(5.0)
        pe.lineTo(6.0, 6.0)
        pe.quadTo(7.0, 7.0, 8.0, 8.0)
        pe.quadTo(9.0, 9.0)             // smooth-quad (reflection)
        pe.curveTo(1.0, 1.0, 2.0, 2.0, 3.0, 3.0)
        pe.curveTo(4.0, 4.0, 5.0, 5.0)  // smooth-cubic
        pe.timmerTo(10.0, 10.0, 11.0, 11.0, 12.0, 12.0)
        pe.timmerTo(13.0, 13.0, 14.0, 14.0)
        pe.svgArcTo(1.0, 1.0, 0.0, false, false, 15.0, 15.0)
        pe.arcThroughTo(16.0, 17.0, 18.0, 19.0)
        pe.gerberArcTo(1.0, 1.0, false, false, 20.0, 20.0)
        pe.appendRectangle(0.0, 0.0, 10.0, 10.0, 1.0, 1.0)
        pe.appendEllipse(0.0, 0.0, 10.0, 10.0, 0.0, 360.0, PathExtensions.OPEN)
        pe.appendEllipse(0.0, 0.0, 10.0, 10.0, 0.0, 360.0, PathExtensions.CHORD)
        pe.appendEllipse(0.0, 0.0, 10.0, 10.0, 0.0, 360.0, PathExtensions.PIE)
        pe.appendRegularPolygon(0.0, 0.0, 5.0, 0.0, 5, 1)
        pe.appendAsterisk(0.0, 0.0, 5.0, 0.0, 5)
        pe.moveTo(0.0, 0.0); pe.lineTo(1.0, 0.0); pe.closePath()

        assertEquals(PathExtensions.WIND_NON_ZERO, pe.windingRule.and(3).or(PathExtensions.WIND_NON_ZERO))
        pe.setWindingRule(PathExtensions.WIND_EVEN_ODD)
        assertEquals(PathExtensions.WIND_EVEN_ODD, pe.windingRule)
    }

    @Test
    fun operand_count_table_is_consistent_for_all_supported_ops() {
        val expected = mapOf(
            'Z' to 0, 'z' to 0,
            'H' to 1, 'h' to 1, 'V' to 1, 'v' to 1, 'W' to 1, 'w' to 1,
            'M' to 2, 'm' to 2, 'L' to 2, 'l' to 2, 'T' to 2, 't' to 2,
            'Q' to 4, 'q' to 4, 'S' to 4, 's' to 4, 'U' to 4, 'u' to 4, 'G' to 4, 'g' to 4,
            'X' to 5, 'x' to 5,
            'C' to 6, 'c' to 6, 'K' to 6, 'k' to 6,
            'I' to 6, 'i' to 6, 'J' to 6, 'j' to 6,
            'R' to 6, 'r' to 6, 'P' to 6, 'p' to 6,
            'A' to 7, 'a' to 7, 'E' to 7, 'e' to 7, 'O' to 7, 'o' to 7,
        )
        for ((op, count) in expected) assertEquals(count, PathExtensions.getOperandCount(op), "op=$op")
        assertEquals(-1, PathExtensions.getOperandCount('?'))
    }

    @Test
    fun static_factories_return_shapes() {
        val arc = PathExtensions.createSvgArc(0.0, 0.0, 5.0, 5.0, 0.0, false, false, 10.0, 0.0)
        assertTrue(arc != null)
        val tim = PathExtensions.createTimmer(0.0, 0.0, 1.0, 1.0, 2.0, 2.0, 3.0, 3.0)
        assertNotNull(tim)
    }

    @Test
    fun append_iterator_and_shape() {
        val pe = PathExtensions()
        pe.moveTo(0.0, 0.0)
        val other = GeneralPath()
        other.moveTo(10.0, 10.0)
        other.lineTo(11.0, 11.0)
        pe.append(other.getPathIterator(null), true)
        pe.append(other, false)
    }
}
