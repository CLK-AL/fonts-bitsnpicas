package com.kreative.bitsnpicas.coverage

import com.kreative.bitsnpicas.exporter.KbitsBitmapFontExporter
import com.kreative.bitsnpicas.importer.KbitsBitmapFontImporter
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Exercises the native .kbits binary format importer + exporter. */
class KbitsRoundTripTest {

    @Test
    fun round_trip_synthetic_font_via_bytes() {
        val bm = TestFonts.tinyFont()
        val exporter = KbitsBitmapFontExporter()
        val bytes = exporter.exportFontToBytes(bm)
        assertTrue(bytes.isNotEmpty())
        val back = KbitsBitmapFontImporter().importFont(bytes)
        assertEquals(1, back.size)
        val rt = back[0]
        assertTrue(rt.containsCharacter('A'.code))
        assertEquals("Tiny", rt.getName(com.kreative.bitsnpicas.Font.NAME_FAMILY))
    }

    @Test
    fun round_trip_stream_and_file() {
        val bm = TestFonts.tinyFont()
        val exporter = KbitsBitmapFontExporter()

        val os = ByteArrayOutputStream()
        exporter.exportFontToStream(bm, os)
        val bytes = os.toByteArray()
        KbitsBitmapFontImporter().importFont(ByteArrayInputStream(bytes)).also {
            assertEquals(1, it.size)
        }

        val tmp = File.createTempFile("kbits-", ".kbits")
        try {
            exporter.exportFontToFile(bm, tmp)
            val fs = KbitsBitmapFontImporter().importFont(tmp)
            assertEquals(1, fs.size)
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun real_corpus_kbits_import() {
        val dir = File("fonts/commodore")
        val files = dir.listFiles { f -> f.extension == "kbits" }
        assertTrue(files != null && files.isNotEmpty(), "corpus present")
        for (f in files!!) {
            val fonts = KbitsBitmapFontImporter().importFont(f)
            assertTrue(fonts.isNotEmpty(), "empty parse: ${f.name}")
        }
    }

    @Test
    fun bad_magic_rejected() {
        val bogus = ByteArray(64)
        assertFailsWith<java.io.IOException> {
            KbitsBitmapFontImporter().importFont(bogus)
        }
    }
}
