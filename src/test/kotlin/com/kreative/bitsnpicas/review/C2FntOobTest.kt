package com.kreative.bitsnpicas.review

import com.kreative.bitsnpicas.importer.FNTBitmapFontImporter
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import kotlin.test.assertFailsWith

/**
 * Critical finding C2 - Unchecked array access in FNT glyph reader.
 *
 * FNTBitmapFontImporter computes `dx` from `geOffset[i]` (a 16-bit value
 * read from the file header) and uses it to index into `data[dx]` with no
 * bounds validation. A crafted .FNT whose glyph offset points past the end
 * of the data buffer must fail cleanly with IOException rather than crash
 * with ArrayIndexOutOfBoundsException.
 */
class C2FntOobTest {

    private fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
    private fun le32(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte()
    )

    @Test
    fun malformed_fnt_with_oob_glyph_offset_does_not_throw_AIOOBE() {
        // Build a minimal v1 FNT (magic=1). The pre-glyph-table header for v1
        // is 117 bytes (0..116). Glyph-table entries follow, each 4 bytes
        // (width u16 + offset u16). One entry plus a sentinel = 8 bytes.
        // Then at least 1 trailing data byte so readFully succeeds.
        val h = ByteArrayOutputStream()

        // Offset 0: magic (u16 big-endian, read by readUnsignedShort)
        h.write(byteArrayOf(0x00, 0x01))
        // Offset 2: size (u32 little-endian) -- patch after knowing total length
        val sizePos = h.size()
        h.write(le32(0))

        // Offset 6: 60-byte copyright
        h.write(ByteArray(60))
        // 66: type (u16 LE)
        h.write(le16(0))
        // 68: points
        h.write(le16(10))
        // 70: vertRes
        h.write(le16(96))
        // 72: horizRes
        h.write(le16(96))
        // 74: ascent
        h.write(le16(8))
        // 76: internalLeading
        h.write(le16(0))
        // 78: externalLeading
        h.write(le16(0))
        // 80: italic
        h.write(byteArrayOf(0))
        // 81: underline
        h.write(byteArrayOf(0))
        // 82: strikeOut
        h.write(byteArrayOf(0))
        // 83: weight (u16)
        h.write(le16(400))
        // 85: charSet (u8)
        h.write(byteArrayOf(0))
        // 86: pixWidth (u16)
        h.write(le16(8))
        // 88: pixHeight (u16)
        h.write(le16(8))
        // 90: pitchAndFamily (u8)
        h.write(byteArrayOf(0))
        // 91: avgWidth (u16)
        h.write(le16(8))
        // 93: maxWidth (u16)
        h.write(le16(8))
        // 95: firstChar (u8)
        h.write(byteArrayOf(0x41))
        // 96: lastChar (u8)  -> n = lastChar - firstChar + 2 = 2
        h.write(byteArrayOf(0x41))
        // 97: defaultChar (u8)
        h.write(byteArrayOf(0x41))
        // 98: breakChar (u8)
        h.write(byteArrayOf(0x20))
        // 99: widthBytes (u16)
        h.write(le16(1))
        // 101: device (u32)
        h.write(le32(0))
        // 105: face (u32) - must point inside the file
        h.write(le32(6))
        // 109: bitsPointer (u32)
        h.write(le32(0))
        // 113: bitsOffset (u32)
        h.write(le32(0))
        // 117: reserved (u8)
        h.write(byteArrayOf(0))
        // Offset 118 onwards: glyph table for v1 (width u16, offset u16 each)
        //   entry 0: width=8, offset=0x7FFF (well past the data buffer)
        h.write(le16(8))
        h.write(le16(0x7FFF))
        //   entry 1 (sentinel after lastChar): width=0, offset=0
        h.write(le16(0))
        h.write(le16(0))

        val bytes = h.toByteArray()
        // Patch the size field (little-endian u32 at offset 2) to match.
        val size = bytes.size
        bytes[sizePos] = (size and 0xFF).toByte()
        bytes[sizePos + 1] = ((size shr 8) and 0xFF).toByte()
        bytes[sizePos + 2] = ((size shr 16) and 0xFF).toByte()
        bytes[sizePos + 3] = ((size shr 24) and 0xFF).toByte()

        val importer = FNTBitmapFontImporter()

        // Must surface as IOException, not AIOOBE.
        assertFailsWith<java.io.IOException> {
            importer.importFont(bytes)
        }
    }
}
