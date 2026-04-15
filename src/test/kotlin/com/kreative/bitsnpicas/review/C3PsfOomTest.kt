package com.kreative.bitsnpicas.review

import com.kreative.bitsnpicas.importer.PSFBitmapFontImporter
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.assertFailsWith

/**
 * Critical finding C3 - Unbounded allocations in PSF importer (OOM).
 *
 * PSFBitmapFontImporter reads `headerSize` and `numGlyphs` from the file
 * and uses them to allocate arrays without sanity-checking. A malicious
 * PSF with numGlyphs=0x7FFFFFFF triggers OutOfMemoryError.
 *
 * After the fix, the importer rejects unreasonably large values with an
 * IOException.
 */
class C3PsfOomTest {

    @Test
    fun psfv2_with_pathological_numGlyphs_is_rejected() {
        val baos = ByteArrayOutputStream()
        val out = DataOutputStream(baos)

        // PSFv2 magic (little-endian).
        out.writeInt(Integer.reverseBytes(0x864AB572.toInt()))
        // version = 0
        out.writeInt(0)
        // headerSize = 32
        out.writeInt(Integer.reverseBytes(32))
        // flags = 0
        out.writeInt(0)
        // numGlyphs = 0x7FFFFFFF (pathological)
        out.writeInt(Integer.reverseBytes(0x7FFFFFFF))
        // charSize = 16
        out.writeInt(Integer.reverseBytes(16))
        // height = 16
        out.writeInt(Integer.reverseBytes(16))
        // width = 8
        out.writeInt(Integer.reverseBytes(8))

        val bytes = baos.toByteArray()
        val importer = PSFBitmapFontImporter()

        assertFailsWith<java.io.IOException> {
            importer.importFont(bytes)
        }
    }

    @Test
    fun psfv2_with_pathological_headerSize_is_rejected() {
        val baos = ByteArrayOutputStream()
        val out = DataOutputStream(baos)

        out.writeInt(Integer.reverseBytes(0x864AB572.toInt()))
        out.writeInt(0)
        // headerSize = 0x40000000 (pathological)
        out.writeInt(Integer.reverseBytes(0x40000000))
        out.writeInt(0)
        out.writeInt(Integer.reverseBytes(256))
        out.writeInt(Integer.reverseBytes(16))
        out.writeInt(Integer.reverseBytes(16))
        out.writeInt(Integer.reverseBytes(8))

        val bytes = baos.toByteArray()
        val importer = PSFBitmapFontImporter()

        assertFailsWith<java.io.IOException> {
            importer.importFont(bytes)
        }
    }
}
