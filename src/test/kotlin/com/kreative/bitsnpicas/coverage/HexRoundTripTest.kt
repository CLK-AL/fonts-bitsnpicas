package com.kreative.bitsnpicas.coverage

import com.kreative.bitsnpicas.exporter.HexBitmapFontExporter
import com.kreative.bitsnpicas.importer.HexBitmapFontImporter
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Hex format: one line per glyph, "CP:HHHH..." in 4-bit-wide cells. */
class HexRoundTripTest {

    private val sample = """
        0041:000000FF818181FF818181818181
        0042:000000FE8181FE8181FE81818181
        ZZZZ:ignored-bad-cp
        : bad-shape
        0043:GGGG
    """.trimIndent().toByteArray(Charsets.UTF_8)

    @Test
    fun importer_accepts_minimal_hex_and_ignores_malformed_lines() {
        val fonts = HexBitmapFontImporter().importFont(sample)
        assertEquals(1, fonts.size)
        val f = fonts[0]
        assertTrue(f.containsCharacter('A'.code))
        assertTrue(f.containsCharacter('B'.code))
    }

    @Test
    fun importer_via_inputStream_and_file() {
        ByteArrayInputStream(sample).use { bais ->
            val fonts = HexBitmapFontImporter().importFont(bais)
            assertEquals(1, fonts.size)
        }
        val tmp = File.createTempFile("hex-", ".hex")
        tmp.writeBytes(sample)
        try {
            val fonts = HexBitmapFontImporter().importFont(tmp)
            assertEquals(1, fonts.size)
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun exporter_emits_bytes_and_round_trips_into_importer() {
        val font = HexBitmapFontImporter().importFont(sample)[0]
        val bytes = HexBitmapFontExporter().exportFontToBytes(font)
        val again = HexBitmapFontImporter().importFont(bytes)
        assertEquals(1, again.size)
        assertTrue(again[0].containsCharacter('A'.code))

        // Stream variant.
        val os = ByteArrayOutputStream()
        HexBitmapFontExporter().exportFontToStream(font, os)
        assertTrue(os.size() > 0)

        // File variant.
        val tmp = File.createTempFile("hex-out-", ".hex")
        try {
            HexBitmapFontExporter().exportFontToFile(font, tmp)
            assertTrue(tmp.length() > 0)
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun empty_font_yields_empty_array() {
        val empty = "".toByteArray(Charsets.UTF_8)
        val fonts = HexBitmapFontImporter().importFont(empty)
        assertEquals(0, fonts.size)
    }
}
