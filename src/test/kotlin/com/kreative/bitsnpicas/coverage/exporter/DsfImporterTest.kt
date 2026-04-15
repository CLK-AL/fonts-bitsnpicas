package com.kreative.bitsnpicas.coverage.exporter

import com.kreative.bitsnpicas.importer.DSFBitmapFontImporter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Synthesise a minimal DosStartFont (DSF) input and run it through the
 * importer. DSF format 0 uses a vector path string (n/b prefix + direction +
 * count) per glyph; format 1 is a direct H/. grid encoding "AAABBBHHH..."
 * embedded after a 6-char "AAABBB" header (cols/rows in 3-digit decimal).
 *
 * We exercise format 1 (simpler to reproduce) for all 95 glyphs.
 */
class DsfImporterTest {

    @Test
    fun importer_format1_synthetic_input() {
        val cols = 3
        val rows = 5
        // One glyph row data: a 3x5 box of "H" pixels.
        val gridChars = buildString {
            for (y in 0 until rows) for (x in 0 until cols) append('H')
        }
        // Each line is "AAABBBgrid, width" where AAABBB are 3-digit cols/rows.
        val header3 = "%03d%03d".format(cols, rows)
        val glyphLine = "$header3$gridChars, $cols"

        val sb = StringBuilder()
        sb.append("DosStartFont\n")
        sb.append("Tiny DSF\n")
        sb.append("1, $rows\n")          // format=1, ascent=rows
        for (i in 0 until 95) sb.append(glyphLine).append('\n')

        val bytes = sb.toString().toByteArray(Charsets.UTF_8)
        val out = DSFBitmapFontImporter().importFont(bytes)
        assertEquals(1, out.size)
        // Every glyph slot 0..94 (chars 32..126) should be present.
        assertEquals(95, out[0].characters(false).size)
    }

    @Test
    fun importer_format0_synthetic_input() {
        // Format 0: per-glyph path. Use empty path with no movement -> empty glyph.
        // Per DSFTokenIterator, an empty path yields an empty bitmap.
        // The simplest valid path is "" (empty string) plus a width.
        val sb = StringBuilder()
        sb.append("DosStartFont\n")
        sb.append("Tiny DSF\n")
        sb.append("0\n") // format=0, no ascent given (initial 0)
        // Each glyph line for format 0 is "path, width". Use width 4.
        // A simple two-pixel diagonal: "br1 br1" - but token parser is greedy,
        // we'll just use a single pixel via "be1" (bring then end at +1).
        // Actually simplest: empty path -> single-cell bitmap.
        for (i in 0 until 95) sb.append(", 4\n")

        val bytes = sb.toString().toByteArray(Charsets.UTF_8)
        // Should not throw.
        val out = DSFBitmapFontImporter().importFont(bytes)
        // Result may be a single empty font.
        assertEquals(1, out.size)
    }

    @Test
    fun importer_returns_empty_for_unrecognised_header() {
        val bytes = "NOT A DSF FILE\n".toByteArray()
        val out = DSFBitmapFontImporter().importFont(bytes)
        assertEquals(0, out.size)
    }

    @Test
    fun importer_returns_empty_for_unsupported_format() {
        val bytes = "DosStartFont\nFooFont\n7\n".toByteArray()
        val out = DSFBitmapFontImporter().importFont(bytes)
        assertEquals(0, out.size)
    }
}
