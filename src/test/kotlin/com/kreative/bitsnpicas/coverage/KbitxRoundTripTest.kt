package com.kreative.bitsnpicas.coverage

import com.kreative.bitsnpicas.GlyphPair
import com.kreative.bitsnpicas.exporter.KbitxBitmapFontExporter
import com.kreative.bitsnpicas.importer.KbitxBitmapFontImporter
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KbitxRoundTripTest {

    @Test
    fun round_trip_kbitx_with_kerning_and_named_glyphs() {
        val bm = TestFonts.tinyFont()
        bm.setKernPair(GlyphPair('A'.code, 'B'.code), -1)
        bm.setKernPair(GlyphPair('A'.code, ".notdef"), 2)
        bm.setKernPair(GlyphPair(".notdef", 'A'.code), 3)
        bm.setKernPair(GlyphPair(".notdef", ".notdef"), 5)

        val exporter = KbitxBitmapFontExporter()
        val bytes = exporter.exportFontToBytes(bm)
        assertTrue(bytes.isNotEmpty())
        val importer = KbitxBitmapFontImporter()
        val back = importer.importFont(bytes)
        assertEquals(1, back.size)
        assertTrue(back[0].containsCharacter('A'.code))
        assertTrue(back[0].containsNamedGlyph(".notdef"))
        assertEquals(-1, back[0].getKernPair(GlyphPair('A'.code, 'B'.code)))

        // Stream and file variants.
        val os = ByteArrayOutputStream()
        exporter.exportFontToStream(bm, os)
        importer.importFont(ByteArrayInputStream(os.toByteArray()))

        val f = File.createTempFile("kbitx-", ".kbitx")
        try {
            exporter.exportFontToFile(bm, f)
            importer.importFont(f)
        } finally { f.delete() }
    }
}
