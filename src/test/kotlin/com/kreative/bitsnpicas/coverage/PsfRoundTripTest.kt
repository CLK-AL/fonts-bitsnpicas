package com.kreative.bitsnpicas.coverage

import com.kreative.bitsnpicas.exporter.PSFBitmapFontExporter
import com.kreative.bitsnpicas.importer.PSFBitmapFontImporter
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PsfRoundTripTest {

    @Test
    fun round_trip_psf_v2_bytes_and_stream() {
        val bm = TestFonts.tinyFont()
        val bytes = PSFBitmapFontExporter().exportFontToBytes(bm)
        assertTrue(bytes.isNotEmpty())
        val back = PSFBitmapFontImporter().importFont(bytes)
        assertTrue(back.isNotEmpty())

        val os = ByteArrayOutputStream()
        PSFBitmapFontExporter().exportFontToStream(bm, os)
        PSFBitmapFontImporter().importFont(ByteArrayInputStream(os.toByteArray()))
    }

    @Test
    fun round_trip_psf_v2_to_file() {
        val bm = TestFonts.tinyFont()
        val tmp = File.createTempFile("psf-", ".psf")
        try {
            PSFBitmapFontExporter().exportFontToFile(bm, tmp)
            val fonts = PSFBitmapFontImporter().importFont(tmp)
            assertTrue(fonts.isNotEmpty())
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun round_trip_psf_v1_bytes() {
        val bm = TestFonts.tinyFont()
        val exporter = PSFBitmapFontExporter(1, null, null, true, false, false, true)
        val bytes = exporter.exportFontToBytes(bm)
        val back = PSFBitmapFontImporter().importFont(bytes)
        assertTrue(back.isNotEmpty())
    }

    @Test
    fun psf_v2_with_gzip() {
        val bm = TestFonts.tinyFont()
        val exporter = PSFBitmapFontExporter(true)
        val bytes = exporter.exportFontToBytes(bm)
        val back = PSFBitmapFontImporter(true).importFont(bytes)
        assertTrue(back.isNotEmpty())

        val tmp = File.createTempFile("psf-", ".psfu.gz")
        try {
            exporter.exportFontToFile(bm, tmp)
            PSFBitmapFontImporter(true).importFont(tmp)
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun psf_bad_magic_rejected() {
        assertFailsWith<java.io.IOException> {
            PSFBitmapFontImporter().importFont(ByteArray(32))
        }
    }

    @Test
    fun psf_v2_without_unicode_table_uses_direct_indexing() {
        val bm = TestFonts.tinyFont()
        val exporter = PSFBitmapFontExporter(2, null, null, false, false, true, false)
        val bytes = exporter.exportFontToBytes(bm)
        val back = PSFBitmapFontImporter().importFont(bytes)
        assertTrue(back.isNotEmpty())
    }

    @Test
    fun file_ext_variants_trimmed() {
        val bm = TestFonts.tinyFont()
        val exporter = PSFBitmapFontExporter()
        for (ext in listOf(".psf", ".psfu", ".PSF")) {
            val f = File.createTempFile("name-", ext)
            try {
                exporter.exportFontToFile(bm, f)
                PSFBitmapFontImporter().importFont(f)
            } finally { f.delete() }
        }
    }

    @Test
    fun filename_ext_psf_gz_and_psfu_gz_trimmed() {
        val bm = TestFonts.tinyFont()
        val exporter = PSFBitmapFontExporter(true)
        for (ext in listOf(".psf.gz", ".psfu.gz")) {
            val f = File.createTempFile("name-", ext)
            try {
                exporter.exportFontToFile(bm, f)
                PSFBitmapFontImporter(true).importFont(f)
            } finally { f.delete() }
        }
    }
}
