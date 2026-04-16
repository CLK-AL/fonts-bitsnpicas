package com.kreative.bitsnpicas.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Stage S4 -- commonMain tests for the pure-Kotlin PSF importer.
 *
 * Constructs PSFv1 and PSFv2 byte arrays inline and verifies:
 * - valid parse produces expected glyph count + dimensions
 * - malformed input (short data, huge numGlyphs) throws PsfParseException (C1/C3)
 */
class PsfImporterTest {

    // ---- helpers for building PSF byte arrays ---------------------------

    /** Build a minimal PSFv1 file. PSFv1: magic(2) + mode(1) + charsize(1) + glyph data. */
    private fun buildPsfV1(
        mode: Int = 0,
        charSize: Int = 8,
        glyphData: ByteArray? = null,
    ): ByteArray {
        // numGlyphs = 256 when mode bit0 = 0, 512 when mode bit0 = 1
        val numGlyphs = if ((mode and 1) == 0) 256 else 512
        val data = glyphData ?: ByteArray(numGlyphs * charSize)
        val buf = ByteArray(4 + data.size)
        // Magic: 0x36, 0x04
        buf[0] = 0x36
        buf[1] = 0x04
        buf[2] = mode.toByte()
        buf[3] = charSize.toByte()
        data.copyInto(buf, 4)
        return buf
    }

    /**
     * Build a minimal PSFv2 file with configurable header fields.
     * For pathological values (C3 tests), pass `headerOnly = true` to avoid
     * allocating huge glyphData arrays.
     */
    private fun buildPsfV2(
        headerSize: Int = 32,
        flags: Int = 0,
        numGlyphs: Int = 1,
        charSize: Int = 16,
        height: Int = 16,
        width: Int = 8,
        glyphData: ByteArray? = null,
        unicodeSuffix: ByteArray? = null,
        headerOnly: Boolean = false,
    ): ByteArray {
        val data = if (headerOnly) ByteArray(0) else (glyphData ?: ByteArray(numGlyphs * charSize))
        val extraHeader = if (!headerOnly && headerSize > 32) ByteArray(headerSize - 32) else ByteArray(0)
        val suffix = unicodeSuffix ?: ByteArray(0)
        val totalSize = 32 + extraHeader.size + data.size + suffix.size
        val buf = ByteArray(totalSize)
        var pos = 0

        // PSFv2 magic: little-endian 0x864AB572
        // As bytes: 0x72, 0xB5, 0x4A, 0x86
        buf[pos++] = 0x72.toByte()
        buf[pos++] = 0xB5.toByte()
        buf[pos++] = 0x4A.toByte()
        buf[pos++] = 0x86.toByte()

        // version = 0 (4 bytes LE)
        putIntLE(buf, pos, 0); pos += 4
        putIntLE(buf, pos, headerSize); pos += 4
        putIntLE(buf, pos, flags); pos += 4
        putIntLE(buf, pos, numGlyphs); pos += 4
        putIntLE(buf, pos, charSize); pos += 4
        putIntLE(buf, pos, height); pos += 4
        putIntLE(buf, pos, width); pos += 4

        // Extra header bytes
        extraHeader.copyInto(buf, pos)
        pos += extraHeader.size

        // Glyph data
        data.copyInto(buf, pos)
        pos += data.size

        // Unicode suffix
        suffix.copyInto(buf, pos)

        return buf
    }

    private fun putIntLE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset + 0] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    // ---- PSFv1 tests ----------------------------------------------------

    @Test
    fun `PSFv1 valid 256-glyph font produces expected glyph count`() {
        val input = buildPsfV1(mode = 0, charSize = 8)
        val font = PsfImporter.read(input)
        assertEquals(256, font.glyphs.size)
    }

    @Test
    fun `PSFv1 valid 512-glyph font produces expected glyph count`() {
        val input = buildPsfV1(mode = 1, charSize = 8)
        val font = PsfImporter.read(input)
        assertEquals(512, font.glyphs.size)
    }

    @Test
    fun `PSFv1 glyph with all-set bits has correct dimensions`() {
        // charSize=1 means height=1, width=8 (always 8 for v1)
        // Fill glyph 0 with 0xFF so contraction doesn't strip it
        val numGlyphs = 256
        val data = ByteArray(numGlyphs)
        data[0] = 0xFF.toByte()
        val input = buildPsfV1(mode = 0, charSize = 1, glyphData = data)
        val font = PsfImporter.read(input)
        val glyph = font.glyphs[0]!!
        assertEquals(1, glyph.height)
        assertEquals(8, glyph.width)
    }

    @Test
    fun `PSFv1 glyph bitmap content is parsed correctly`() {
        // Create a 1-row, 8-wide glyph (charSize = 1)
        // Fill first glyph with 0xFF (all bits set)
        val numGlyphs = 256
        val data = ByteArray(numGlyphs)
        data[0] = 0xFF.toByte()
        val input = buildPsfV1(mode = 0, charSize = 1, glyphData = data)
        val font = PsfImporter.read(input)
        val glyph = font.glyphs[0]!!
        // width=8, height=1, all set: row 0 should be [FF, FF, FF, FF, FF, FF, FF, FF]
        val row = glyph.bitmap[0].toList()
        assertEquals(List(8) { 0xFF }, row)
    }

    // ---- PSFv2 tests ----------------------------------------------------

    @Test
    fun `PSFv2 valid single-glyph font produces expected glyph count`() {
        val input = buildPsfV2(numGlyphs = 1, charSize = 16, height = 16, width = 8)
        val font = PsfImporter.read(input)
        assertEquals(1, font.glyphs.size)
    }

    @Test
    fun `PSFv2 glyph with non-zero data has correct dimensions`() {
        // 2 rows x 16 cols = charSize 4 (2 bytes per row)
        // Fill with 0xFF so contraction doesn't strip anything
        val data = ByteArray(4) { 0xFF.toByte() }
        val input = buildPsfV2(numGlyphs = 1, charSize = 4, height = 2, width = 16, glyphData = data)
        val font = PsfImporter.read(input)
        val glyph = font.glyphs[0]!!
        assertEquals(2, glyph.height)
        assertEquals(16, glyph.width)
    }

    @Test
    fun `PSFv2 multi-glyph font`() {
        val input = buildPsfV2(numGlyphs = 10, charSize = 2, height = 2, width = 8)
        val font = PsfImporter.read(input)
        assertEquals(10, font.glyphs.size)
    }

    @Test
    fun `PSFv2 extra header bytes are skipped`() {
        val input = buildPsfV2(headerSize = 64, numGlyphs = 1, charSize = 16, height = 16, width = 8)
        val font = PsfImporter.read(input)
        assertEquals(1, font.glyphs.size)
    }

    @Test
    fun `PSFv2 with unicode table maps codepoints`() {
        // 1 glyph, with unicode table mapping it to codepoint 'A' (0x41)
        // PSFv2 unicode table entry: UTF-8 bytes for 'A', then 0xFF terminator
        val unicodeEntry = byteArrayOf(0x41, 0xFF.toByte())
        val input = buildPsfV2(
            flags = 1, // has unicode table
            numGlyphs = 1,
            charSize = 2,
            height = 2,
            width = 8,
            unicodeSuffix = unicodeEntry,
        )
        val font = PsfImporter.read(input)
        assertEquals(1, font.glyphs.size)
        assertTrue(font.glyphs.containsKey(0x41), "Should have codepoint for 'A'")
    }

    // ---- C1: OOB guard tests --------------------------------------------

    @Test
    fun `C1 PSFv2 charSize too small for dimensions throws`() {
        // width=64, height=2 needs 2*8=16 bytes per glyph, but charSize=2
        assertFailsWith<PsfImporter.PsfParseException> {
            PsfImporter.read(
                buildPsfV2(numGlyphs = 1, charSize = 2, height = 2, width = 64)
            )
        }
    }

    @Test
    fun `C1 PSFv2 zero-width zero-height glyph is accepted`() {
        val input = buildPsfV2(numGlyphs = 1, charSize = 0, height = 0, width = 0, glyphData = ByteArray(0))
        val font = PsfImporter.read(input)
        assertEquals(1, font.glyphs.size)
    }

    // ---- C3: OOM cap tests ----------------------------------------------

    @Test
    fun `C3 pathological numGlyphs is rejected`() {
        assertFailsWith<PsfImporter.PsfParseException> {
            PsfImporter.read(
                buildPsfV2(numGlyphs = 0x7FFFFFFF, charSize = 16, height = 16, width = 8, headerOnly = true)
            )
        }
    }

    @Test
    fun `C3 pathological headerSize is rejected`() {
        assertFailsWith<PsfImporter.PsfParseException> {
            PsfImporter.read(
                buildPsfV2(headerSize = 0x40000000, numGlyphs = 1, charSize = 16, height = 16, width = 8, headerOnly = true)
            )
        }
    }

    @Test
    fun `C3 pathological charSize is rejected`() {
        assertFailsWith<PsfImporter.PsfParseException> {
            PsfImporter.read(
                buildPsfV2(numGlyphs = 1, charSize = 0x7FFFFFFF, height = 16, width = 8, headerOnly = true)
            )
        }
    }

    @Test
    fun `C3 pathological dimensions are rejected`() {
        assertFailsWith<PsfImporter.PsfParseException> {
            PsfImporter.read(
                buildPsfV2(numGlyphs = 1, charSize = 16, height = 0x7FFF, width = 0x7FFF, headerOnly = true)
            )
        }
    }

    // ---- Bad magic tests ------------------------------------------------

    @Test
    fun `bad magic number is rejected`() {
        assertFailsWith<PsfImporter.PsfParseException> {
            PsfImporter.read(byteArrayOf(0x00, 0x00, 0x00, 0x00))
        }
    }

    @Test
    fun `truncated input is rejected`() {
        assertFailsWith<PsfImporter.PsfParseException> {
            PsfImporter.read(byteArrayOf(0x36, 0x04))
        }
    }

    // ---- Font metadata tests --------------------------------------------

    @Test
    fun `PSFv2 font metadata has correct ascent and dimensions`() {
        val input = buildPsfV2(numGlyphs = 1, charSize = 2, height = 2, width = 8)
        val font = PsfImporter.read(input)
        assertEquals(2, font.emAscent)
        assertEquals(0, font.emDescent)
        assertEquals(8, font.newGlyphWidth)
    }
}
