package com.kreative.bitsnpicas.review

import com.kreative.bitsnpicas.importer.PSFBitmapFontImporter
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.assertFailsWith

/**
 * Critical finding C1 - Array-index-out-of-bounds in PSF glyph reader.
 *
 * PSFBitmapFontImporter reads a per-glyph byte[] of `charSize` bytes, then
 * walks it with a nested (y, x) loop whose byte index `j` advances once per
 * x-byte. If the header's `width` exceeds `charSize / height * 8`, `j` runs
 * past `data.length` and the importer throws AIOOBE.
 *
 * The importer must reject the malformed file with a checked IOException,
 * not crash with AIOOBE.
 */
class C1PsfOobTest {

    @Test
    fun malformed_psfv1_with_oversize_width_does_not_throw_AIOOBE() {
        // PSFv1: 4-byte header (magic 0x3604, mode, charsize).
        // Set charsize = 2 (height=2, only 1 byte per row). But the importer
        // always uses width=8 for v1 -- to force the OOB we need a v2 file
        // with width > 8*(charSize/height).
        val baos = ByteArrayOutputStream()
        val out = DataOutputStream(baos)

        // PSFv2 magic (little-endian 0x864AB572).
        out.writeInt(Integer.reverseBytes(0x864AB572.toInt()))
        // version = 0
        out.writeInt(0)
        // headerSize = 32
        out.writeInt(Integer.reverseBytes(32))
        // flags = 0
        out.writeInt(0)
        // numGlyphs = 1
        out.writeInt(Integer.reverseBytes(1))
        // charSize = 2 bytes per glyph
        out.writeInt(Integer.reverseBytes(2))
        // height = 2
        out.writeInt(Integer.reverseBytes(2))
        // width = 64  (would require 2*8 = 16 bytes per glyph, only 2 available)
        out.writeInt(Integer.reverseBytes(64))

        // Glyph data: only 2 bytes, but the (2 row x 64 col) loop will walk
        // the byte index far past the buffer end.
        out.write(byteArrayOf(0x00, 0x00))

        val bytes = baos.toByteArray()
        val importer = PSFBitmapFontImporter()

        // Must surface as IOException, not AIOOBE.
        assertFailsWith<java.io.IOException> {
            importer.importFont(bytes)
        }
    }
}
