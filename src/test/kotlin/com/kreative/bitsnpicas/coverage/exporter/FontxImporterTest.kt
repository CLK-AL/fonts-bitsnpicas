package com.kreative.bitsnpicas.coverage.exporter

import com.kreative.bitsnpicas.BitmapFontGlyph
import com.kreative.bitsnpicas.coverage.TestFonts
import com.kreative.bitsnpicas.exporter.FONTXBitmapFontExporter
import com.kreative.bitsnpicas.importer.FONTXBitmapFontImporter
import com.kreative.unicode.data.GlyphList
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import kotlin.test.assertTrue

/**
 * Cover the FONTXBitmapFontImporter's alternative constructors and double-byte
 * branch by round-tripping fonts produced by the matching exporter.
 */
class FontxImporterTest {

    @Test
    fun importer_glyphList_singleByte_constructor() {
        val cps = IntArray(256) { i -> if (i < 4) (0x41 + i) else -1 }
        val gl = GlyphList(cps, "TestEncoding")
        val bytes = FONTXBitmapFontExporter(gl).exportFontToBytes(TestFonts.tinyFont())
        FONTXBitmapFontImporter(gl).importFont(bytes)
    }

    @Test
    fun importer_glyphList_and_doubleByte_named_constructor() {
        val gl = GlyphList(IntArray(256) { -1 }, "x")
        FONTXBitmapFontImporter(gl, "CP932")
    }

    @Test
    fun importer_doubleByte_named_constructor() {
        val bm = TestFonts.tinyFont()
        for (cp in 0x4E00..0x4E03) {
            bm.putCharacter(cp, BitmapFontGlyph(arrayOf(byteArrayOf(0xFF.toByte())), 0, 1, 1))
        }
        val bytes = FONTXBitmapFontExporter("CP932").exportFontToBytes(bm)
        FONTXBitmapFontImporter("CP932").importFont(bytes)
    }

    @Test
    fun importer_rejects_bad_magic() {
        val bytes = ByteArray(20) // garbage zeroes - magic mismatch
        assertThrows<IOException> {
            FONTXBitmapFontImporter().importFont(bytes)
        }
    }

    @Test
    fun importer_rejects_bad_subMagic() {
        // Correct first int "FONT" but wrong sub-magic -> second IOException branch.
        val bytes = byteArrayOf(0x46, 0x4F, 0x4E, 0x54, 0x00, 0x00) +
                ByteArray(20)
        assertThrows<IOException> {
            FONTXBitmapFontImporter().importFont(bytes)
        }
    }

    @Test
    fun importer_round_trip_default_double_byte() {
        val bm = TestFonts.tinyFont()
        bm.putCharacter(0x30A2, BitmapFontGlyph(arrayOf(byteArrayOf(0xFF.toByte())), 0, 1, 1))
        val bytes = FONTXBitmapFontExporter(true).exportFontToBytes(bm)
        val out = FONTXBitmapFontImporter().importFont(bytes)
        assertTrue(out.isNotEmpty())
    }
}
