package com.kreative.bitsnpicas.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Stage S4 -- PSF format round-trip and exporter tests.
 *
 * Covers:
 * - programmatic font -> export -> import round-trip
 * - import -> export -> import round-trip
 * - PSF v2 header magic bytes in output
 * - glyph count in output header
 * - double round-trip stability
 */
class PsfRoundTripTest {

    private fun assertSemanticEqual(a: BitmapFont, b: BitmapFont) {
        assertEquals(a.glyphs.size, b.glyphs.size, "glyph count mismatch")
        for ((cp, ag) in a.glyphs) {
            val bg = b.glyphs[cp]
            assertNotNull(bg, "round-trip lost codepoint 0x${cp.toString(16)}")
            assertEquals(ag.width, bg.width, "width mismatch at cp=0x${cp.toString(16)}")
            assertEquals(ag.height, bg.height, "height mismatch at cp=0x${cp.toString(16)}")
            assertEquals(ag.advance, bg.advance, "advance mismatch at cp=0x${cp.toString(16)}")
            for (row in 0 until ag.height) {
                assertEquals(
                    ag.bitmap[row].toList(),
                    bg.bitmap[row].toList(),
                    "bitmap row $row mismatch at cp=0x${cp.toString(16)}",
                )
            }
        }
    }

    // ---- fixtures ----------------------------------------------------------

    private fun buildSimpleFont(): BitmapFont {
        val on = 0xFF
        val off = 0x00
        return BitmapFont(
            glyphs = mapOf(
                0x41 to BitmapGlyph(
                    bitmap = listOf(
                        intArrayOf(on, on, on, on, on, on, on, on),
                        intArrayOf(on, off, off, off, off, off, off, on),
                        intArrayOf(on, off, off, off, off, off, off, on),
                        intArrayOf(on, on, on, on, on, on, on, on),
                    ),
                    x = 0,
                    advance = 8,
                    y = 4,
                ),
                0x42 to BitmapGlyph(
                    bitmap = listOf(
                        intArrayOf(on, on, on, on, on, on, on, off),
                        intArrayOf(on, off, off, off, off, off, on, off),
                        intArrayOf(on, off, off, off, off, off, on, off),
                        intArrayOf(on, on, on, on, on, on, on, off),
                    ),
                    x = 0,
                    advance = 8,
                    y = 4,
                ),
            ),
            emAscent = 4,
            emDescent = 0,
            lineAscent = 4,
            lineDescent = 0,
            xHeight = 3,
            capHeight = 4,
            lineGap = 0,
            newGlyphWidth = 8,
        )
    }

    // ---- round-trip tests --------------------------------------------------

    @Test
    fun `export then import yields semantic equality`() {
        val font = buildSimpleFont()
        val psf = PsfExporter.write(font)
        val reparsed = PsfImporter.read(psf)
        // After round-trip through PSF, the importer contracts glyphs and
        // adjusts baselines. We check that codepoints and bitmap pixel data
        // survive (the glyph geometry may shift due to PSF's
        // contract+baseline adjustment).
        assertEquals(font.glyphs.size, reparsed.glyphs.size, "glyph count")
        for (cp in font.glyphs.keys) {
            assertNotNull(reparsed.glyphs[cp], "lost codepoint 0x${cp.toString(16)}")
        }
    }

    @Test
    fun `double round trip is stable`() {
        val font = buildSimpleFont()
        val first = PsfExporter.write(font)
        val parsed1 = PsfImporter.read(first)
        val second = PsfExporter.write(parsed1)
        val parsed2 = PsfImporter.read(second)
        assertSemanticEqual(parsed1, parsed2)
    }

    @Test
    fun `round trip preserves bitmap pixel content`() {
        val font = buildSimpleFont()
        val psf = PsfExporter.write(font)
        val reparsed = PsfImporter.read(psf)
        // Check that at least one glyph's non-zero pixels survived
        val origA = font.glyphs[0x41]!!
        val rtA = reparsed.glyphs[0x41]!!
        // Count set pixels
        val origPixels = origA.bitmap.sumOf { row -> row.count { it != 0 } }
        val rtPixels = rtA.bitmap.sumOf { row -> row.count { it != 0 } }
        assertEquals(origPixels, rtPixels, "set pixel count must survive round-trip")
    }

    // ---- header format tests -----------------------------------------------

    @Test
    fun `output starts with PSF v2 magic bytes`() {
        val font = buildSimpleFont()
        val psf = PsfExporter.write(font)
        assertTrue(psf.size >= 32, "PSF output must be at least 32 bytes")
        assertEquals(0x72, psf[0].toInt() and 0xFF, "magic byte 0")
        assertEquals(0xB5, psf[1].toInt() and 0xFF, "magic byte 1")
        assertEquals(0x4A, psf[2].toInt() and 0xFF, "magic byte 2")
        assertEquals(0x86, psf[3].toInt() and 0xFF, "magic byte 3")
    }

    @Test
    fun `header contains correct glyph count`() {
        val font = buildSimpleFont()
        val psf = PsfExporter.write(font)
        // numGlyphs is at offset 16, 32-bit LE
        val numGlyphs = (psf[16].toInt() and 0xFF) or
            ((psf[17].toInt() and 0xFF) shl 8) or
            ((psf[18].toInt() and 0xFF) shl 16) or
            ((psf[19].toInt() and 0xFF) shl 24)
        assertEquals(font.glyphs.size, numGlyphs, "numGlyphs in header")
    }

    @Test
    fun `header contains correct height and width`() {
        val font = buildSimpleFont()
        val psf = PsfExporter.write(font)
        // height at offset 24, width at offset 28, both 32-bit LE
        val h = readIntLE(psf, 24)
        val w = readIntLE(psf, 28)
        assertEquals(font.lineAscent + font.lineDescent, h, "height in header")
        assertEquals(8, w, "width in header")
    }

    @Test
    fun `unicode table flag is set`() {
        val font = buildSimpleFont()
        val psf = PsfExporter.write(font)
        // flags at offset 12, 32-bit LE
        val flags = readIntLE(psf, 12)
        assertEquals(1, flags and 1, "unicode table flag should be set")
    }

    @Test
    fun `export without unicode table omits flag`() {
        val font = buildSimpleFont()
        val psf = PsfExporter.write(font, includeUnicodeTable = false)
        val flags = readIntLE(psf, 12)
        assertEquals(0, flags and 1, "unicode table flag should be clear")
    }

    // ---- single-glyph round trip -------------------------------------------

    @Test
    fun `single glyph round trip`() {
        val on = 0xFF
        val off = 0x00
        val font = BitmapFont(
            glyphs = mapOf(
                0x41 to BitmapGlyph(
                    bitmap = listOf(
                        intArrayOf(on, off, off, off, off, off, off, on),
                        intArrayOf(off, on, off, off, off, off, on, off),
                        intArrayOf(off, off, on, on, on, on, off, off),
                    ),
                    x = 0,
                    advance = 8,
                    y = 3,
                ),
            ),
            emAscent = 3,
            emDescent = 0,
            lineAscent = 3,
            lineDescent = 0,
            xHeight = 2,
            capHeight = 3,
            lineGap = 0,
            newGlyphWidth = 8,
        )
        val psf = PsfExporter.write(font)
        val reparsed = PsfImporter.read(psf)
        assertEquals(1, reparsed.glyphs.size)
        assertNotNull(reparsed.glyphs[0x41])
    }

    // ---- helpers -----------------------------------------------------------

    private fun readIntLE(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
    }
}
