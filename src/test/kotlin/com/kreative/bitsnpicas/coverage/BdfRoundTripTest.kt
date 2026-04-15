package com.kreative.bitsnpicas.coverage

import com.kreative.bitsnpicas.exporter.BDFBitmapFontExporter
import com.kreative.bitsnpicas.importer.BDFBitmapFontImporter
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Round-trip export/import through the BDF path to cover both classes. */
class BdfRoundTripTest {

    @Test
    fun round_trip_synthetic_font() {
        val bm = TestFonts.tinyFont()
        val bytes = BDFBitmapFontExporter().exportFontToBytes(bm)
        val back = BDFBitmapFontImporter().importFont(bytes)
        assertEquals(1, back.size)
        val rt = back[0]
        assertTrue(rt.containsCharacter('A'.code))
    }

    @Test
    fun exporter_stream_and_file_paths() {
        val bm = TestFonts.tinyFont()
        val os = ByteArrayOutputStream()
        BDFBitmapFontExporter().exportFontToStream(bm, os)
        assertTrue(os.size() > 0)

        val tmp = File.createTempFile("bdf-", ".bdf")
        try {
            BDFBitmapFontExporter().exportFontToFile(bm, tmp)
            assertTrue(tmp.length() > 0)
            val fonts = BDFBitmapFontImporter().importFont(tmp)
            assertEquals(1, fonts.size)
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun importer_handles_various_charset_registries_and_metrics() {
        val src = """
            STARTFONT 2.1
            FONT -Vendor-Family-Style-R-Style--8-8-75-75-c-80-iso10646-1
            SIZE 8 75 75
            FONTBOUNDINGBOX 4 4 0 0
            STARTPROPERTIES 9
            FAMILY_NAME "MyFam"
            WEIGHT_NAME "Regular"
            FONT_VERSION "1.0"
            COPYRIGHT "PD"
            FOUNDRY "FO"
            FONT_ASCENT 6
            FONT_DESCENT 2
            X_HEIGHT 4
            CAP_HEIGHT 6
            ENDPROPERTIES
            CHARS 3
            STARTCHAR .notdef
            ENCODING -1
            DWIDTH 4 0
            BBX 4 4 0 0
            BITMAP
            F0
            90
            90
            F0
            ENDCHAR
            STARTCHAR A
            ENCODING 65
            DWIDTH 4 0
            BBX 4 4 0 0
            BITMAP
            60
            90
            F0
            90
            ENDCHAR
            STARTCHAR B
            ENCODING 66
            DWIDTH bad bad
            BBX bad data
            BITMAP
            ENDCHAR
            ENDFONT
        """.trimIndent().toByteArray(Charsets.UTF_8)
        val fonts = BDFBitmapFontImporter().importFont(src)
        assertEquals(1, fonts.size)
        assertEquals("MyFam", fonts[0].getName(com.kreative.bitsnpicas.Font.NAME_FAMILY))
        assertTrue(fonts[0].containsCharacter('A'.code))
        assertTrue(fonts[0].containsNamedGlyph(".notdef"))
    }

    @Test
    fun importer_handles_font_specific_charset() {
        val src = """
            STARTFONT 2.1
            FONT x
            SIZE 8 75 75
            FONTBOUNDINGBOX 2 2 0 0
            STARTPROPERTIES 2
            CHARSET_REGISTRY "FontSpecific"
            FONT_ASCENT 2
            ENDPROPERTIES
            CHARS 1
            STARTCHAR f0
            ENCODING 17
            DWIDTH 2 0
            BBX 2 2 0 0
            BITMAP
            C0
            C0
            ENDCHAR
            ENDFONT
        """.trimIndent().toByteArray(Charsets.UTF_8)
        val fonts = BDFBitmapFontImporter().importFont(src)
        assertEquals(1, fonts.size)
        // FONT_SPECIFIC encoding should be offset by 0xF000.
        assertTrue(fonts[0].containsCharacter(0xF011))
    }

    @Test
    fun importer_handles_known_mapped_registry() {
        val src = """
            STARTFONT 2.1
            FONT x
            SIZE 8 75 75
            FONTBOUNDINGBOX 2 2 0 0
            STARTPROPERTIES 1
            CHARSET_REGISTRY "JISX0208.1990"
            ENDPROPERTIES
            CHARS 0
            ENDFONT
        """.trimIndent().toByteArray(Charsets.UTF_8)
        val fonts = BDFBitmapFontImporter().importFont(src)
        assertEquals(1, fonts.size)

        val src2 = """
            STARTFONT 2.1
            FONT x
            STARTPROPERTIES 1
            CHARSET_REGISTRY "TOTALLY_UNKNOWN"
            ENDPROPERTIES
            CHARS 0
            ENDFONT
        """.trimIndent().toByteArray(Charsets.UTF_8)
        BDFBitmapFontImporter().importFont(src2) // hits the warning path
    }

    @Test
    fun importer_inputStream_path() {
        val src = "STARTFONT 2.1\nENDFONT\n".toByteArray(Charsets.UTF_8)
        BDFBitmapFontImporter().importFont(ByteArrayInputStream(src))
    }
}
