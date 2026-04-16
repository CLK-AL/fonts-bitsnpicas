package com.kreative.bitsnpicas.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Stage S4 differential-parity gate for the PSF importer.
 *
 * Each valid PSF fixture is imported via both the commonMain
 * `PsfImporter.read` and the frozen Java `JavaLegacyAdapter.importPsfViaJava`.
 * The resulting `BitmapFont` must agree on glyph count, dimensions,
 * and bitmap bytes.
 */
class PsfImporterJvmParityTest {

    // ---- helpers for building PSF byte arrays ---------------------------

    private fun buildPsfV1(
        mode: Int = 0,
        charSize: Int = 8,
        glyphData: ByteArray? = null,
    ): ByteArray {
        val numGlyphs = if ((mode and 1) == 0) 256 else 512
        val data = glyphData ?: ByteArray(numGlyphs * charSize)
        val buf = ByteArray(4 + data.size)
        buf[0] = 0x36
        buf[1] = 0x04
        buf[2] = mode.toByte()
        buf[3] = charSize.toByte()
        data.copyInto(buf, 4)
        return buf
    }

    private fun buildPsfV2(
        headerSize: Int = 32,
        flags: Int = 0,
        numGlyphs: Int = 1,
        charSize: Int = 16,
        height: Int = 16,
        width: Int = 8,
        glyphData: ByteArray? = null,
        unicodeSuffix: ByteArray? = null,
    ): ByteArray {
        val data = glyphData ?: ByteArray(numGlyphs * charSize)
        val extraHeader = if (headerSize > 32) ByteArray(headerSize - 32) else ByteArray(0)
        val suffix = unicodeSuffix ?: ByteArray(0)
        val totalSize = 32 + extraHeader.size + data.size + suffix.size
        val buf = ByteArray(totalSize)
        var pos = 0
        buf[pos++] = 0x72.toByte()
        buf[pos++] = 0xB5.toByte()
        buf[pos++] = 0x4A.toByte()
        buf[pos++] = 0x86.toByte()
        putIntLE(buf, pos, 0); pos += 4
        putIntLE(buf, pos, headerSize); pos += 4
        putIntLE(buf, pos, flags); pos += 4
        putIntLE(buf, pos, numGlyphs); pos += 4
        putIntLE(buf, pos, charSize); pos += 4
        putIntLE(buf, pos, height); pos += 4
        putIntLE(buf, pos, width); pos += 4
        extraHeader.copyInto(buf, pos); pos += extraHeader.size
        data.copyInto(buf, pos); pos += data.size
        suffix.copyInto(buf, pos)
        return buf
    }

    private fun putIntLE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset + 0] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    // ---- parity assertion -----------------------------------------------

    private fun assertParity(input: ByteArray) {
        val kotlin = PsfImporter.read(input)
        val java = JavaLegacyAdapter.importPsfViaJava(input)

        assertEquals(java.glyphs.size, kotlin.glyphs.size, "glyph count mismatch")
        assertEquals(java.newGlyphWidth, kotlin.newGlyphWidth, "newGlyphWidth mismatch")

        // Compare every glyph bitmap
        for ((cp, jGlyph) in java.glyphs) {
            val kGlyph = kotlin.glyphs[cp]
                ?: error("Kotlin result missing codepoint $cp")
            assertEquals(jGlyph.width, kGlyph.width, "width mismatch at cp=$cp")
            assertEquals(jGlyph.height, kGlyph.height, "height mismatch at cp=$cp")
            assertEquals(jGlyph.advance, kGlyph.advance, "advance mismatch at cp=$cp")
            for (row in 0 until jGlyph.height) {
                assertEquals(
                    jGlyph.bitmap[row].toList(),
                    kGlyph.bitmap[row].toList(),
                    "bitmap row $row mismatch at cp=$cp",
                )
            }
        }
    }

    // ---- PSFv1 parity ---------------------------------------------------

    @Test
    fun `PSFv1 256-glyph all-zero parity`() {
        assertParity(buildPsfV1(mode = 0, charSize = 8))
    }

    @Test
    fun `PSFv1 512-glyph all-zero parity`() {
        assertParity(buildPsfV1(mode = 1, charSize = 8))
    }

    @Test
    fun `PSFv1 with non-trivial bitmap content parity`() {
        val numGlyphs = 256
        val data = ByteArray(numGlyphs * 1)
        data[0] = 0xAA.toByte()   // glyph 0: alternating bits
        data[65] = 0xFF.toByte()  // glyph 65 ('A'): all set
        assertParity(buildPsfV1(mode = 0, charSize = 1, glyphData = data))
    }

    // ---- PSFv2 parity ---------------------------------------------------

    @Test
    fun `PSFv2 single glyph parity`() {
        assertParity(buildPsfV2(numGlyphs = 1, charSize = 16, height = 16, width = 8))
    }

    @Test
    fun `PSFv2 multi-glyph parity`() {
        assertParity(buildPsfV2(numGlyphs = 10, charSize = 2, height = 2, width = 8))
    }

    @Test
    fun `PSFv2 wide glyph parity`() {
        // 16px wide = 2 bytes per row, 2 rows = charSize 4
        assertParity(buildPsfV2(numGlyphs = 1, charSize = 4, height = 2, width = 16))
    }

    @Test
    fun `PSFv2 extra header parity`() {
        assertParity(buildPsfV2(headerSize = 64, numGlyphs = 1, charSize = 16, height = 16, width = 8))
    }

    @Test
    fun `PSFv2 with unicode table parity`() {
        // Map single glyph to codepoint 'A' (0x41)
        val unicodeEntry = byteArrayOf(0x41, 0xFF.toByte())
        assertParity(
            buildPsfV2(
                flags = 1,
                numGlyphs = 1,
                charSize = 2,
                height = 2,
                width = 8,
                unicodeSuffix = unicodeEntry,
            )
        )
    }

    @Test
    fun `PSFv2 with non-trivial bitmap content parity`() {
        val charSize = 2  // 2 rows of 1 byte each (8px wide)
        val data = ByteArray(charSize)
        data[0] = 0b01010101.toByte()
        data[1] = 0b10101010.toByte()
        assertParity(
            buildPsfV2(numGlyphs = 1, charSize = charSize, height = 2, width = 8, glyphData = data)
        )
    }
}
