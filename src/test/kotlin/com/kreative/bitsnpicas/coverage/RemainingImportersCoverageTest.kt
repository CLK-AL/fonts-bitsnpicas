package com.kreative.bitsnpicas.coverage

import com.kreative.bitsnpicas.Font
import com.kreative.bitsnpicas.VectorFont
import com.kreative.bitsnpicas.VectorFontGlyph
import com.kreative.bitsnpicas.VectorInstruction
import com.kreative.bitsnpicas.VectorPath
import com.kreative.bitsnpicas.exporter.AmigaBitmapFontExporter
import com.kreative.bitsnpicas.exporter.KpcasVectorFontExporter
import com.kreative.bitsnpicas.exporter.KpcaxVectorFontExporter
import com.kreative.bitsnpicas.importer.AmigaBitmapFontImporter
import com.kreative.bitsnpicas.importer.BinaryBitmapFontImporter
import com.kreative.bitsnpicas.importer.DSFBitmapFontImporter
import com.kreative.bitsnpicas.importer.ImageBitmapFontImporter
import com.kreative.bitsnpicas.importer.KpcasVectorFontImporter
import com.kreative.bitsnpicas.importer.KpcaxVectorFontImporter
import com.kreative.bitsnpicas.importer.S10BitmapFontImporter
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RemainingImportersCoverageTest {

    @Test
    fun binary_importer_preview_and_import() {
        // Fake 128 glyphs of 8 bytes each = 1024 bytes.
        val raw = ByteArray(1024) { (it and 0xFF).toByte() }
        val imp = BinaryBitmapFontImporter()
        val preview = imp.preview(raw)
        assertTrue(preview.isNotEmpty())
        val fonts = imp.importFont(raw)
        assertEquals(1, fonts.size)
        assertTrue(fonts[0].characters(false).isNotEmpty())

        // With encoding mapping.
        imp.encoding = listOf(65, 66, -1)
        imp.importFont(raw)

        // Invert + right-align + flips.
        imp.invert = true; imp.rightAlign = true
        imp.flipBits = true; imp.flipBytes = true
        imp.bitsPerPixel = 2
        imp.importFont(raw)
        imp.preview(raw)

        // Stream and file paths.
        imp.bitsPerPixel = 1; imp.invert = false
        imp.importFont(ByteArrayInputStream(raw))
        val tmp = File.createTempFile("bin-", ".bin")
        try {
            tmp.writeBytes(raw)
            imp.importFont(tmp)
        } finally { tmp.delete() }
    }

    @Test
    fun s10_importer_real_corpus() {
        val f = File("fonts/sabine/SABCHARS.S10")
        assertTrue(f.exists())
        val imp = S10BitmapFontImporter()
        val fonts = imp.importFont(f)
        assertTrue(fonts.isNotEmpty())

        // Bytes + stream paths.
        imp.importFont(f.readBytes())
        imp.importFont(ByteArrayInputStream(f.readBytes()))
    }

    @Test
    fun dsf_importer_synthetic_format0_format1() {
        val sb = StringBuilder()
        sb.append("DosStartFont\n")
        sb.append("MyFont\n")
        sb.append("1, 6\n")              // format=1, ascent=6
        for (i in 0 until 95) {
            // cols=004, rows=006, advance=5, followed by 4*6=24 cells each 'H' or '-'
            val cell = "004006" + ".".repeat(4 * 6)
            sb.append("$cell, 5\n")
        }
        val data = sb.toString().toByteArray(Charsets.UTF_8)
        val fonts = DSFBitmapFontImporter().importFont(data)
        assertTrue(fonts.isNotEmpty())

        // Test via other entry points.
        DSFBitmapFontImporter().importFont(ByteArrayInputStream(data))
        val tmp = File.createTempFile("dsf-", ".dsf")
        try {
            tmp.writeBytes(data)
            DSFBitmapFontImporter().importFont(tmp)
        } finally { tmp.delete() }
    }

    @Test
    fun image_importer_preview_and_import() {
        val img = BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, 48, 48)
        g.color = Color.BLACK
        for (r in 0 until 3) for (c in 0 until 6) {
            g.fillRect(c * 8 + 1, r * 8 + 1, 6, 6)
        }
        g.dispose()

        val imp = ImageBitmapFontImporter().apply {
            cellWidth = 8; cellHeight = 8
            columnCount = 6; rowCount = 3
            startX = 0; startY = 0
        }
        val preview = imp.preview(img)
        assertNotNull(preview.preview)
        assertTrue(preview.points.isNotEmpty())

        val font = imp.importFont(img)
        assertNotNull(font)

        // invert + encoding paths
        imp.invert = true
        imp.encoding = listOf(65, 66, 67, -1, 68)
        imp.importFont(img)

        // Write to temp PNG and use file path.
        val tmp = File.createTempFile("img-font-", ".png")
        try {
            ImageIO.write(img, "png", tmp)
            imp.importFont(tmp)
            imp.importFont(ByteArrayInputStream(tmp.readBytes()))
            imp.importFont(tmp.readBytes())
        } finally { tmp.delete() }
    }

    private fun buildSmallVectorFont(): VectorFont {
        val vf = VectorFont(6.0, 2.0, 6.0, 2.0, 4.0, 6.0, 0.0, 4.0)
        vf.setName(Font.NAME_FAMILY, "V"); vf.setName(Font.NAME_STYLE, "Plain")
        val vp = VectorPath()
        vp.add(VectorInstruction('M', 0.0, 0.0))
        vp.add(VectorInstruction('L', 4.0, 0.0))
        vp.add(VectorInstruction('L', 2.0, -4.0))
        vp.add(VectorInstruction('Z'))
        vf.putCharacter('A'.code, VectorFontGlyph(listOf(vp), 4.0))
        vf.putNamedGlyph(".notdef", VectorFontGlyph(listOf(vp), 4.0))
        return vf
    }

    @Test
    fun kpcas_vector_round_trip() {
        val vf = buildSmallVectorFont()
        val bytes = KpcasVectorFontExporter().exportFontToBytes(vf)
        assertTrue(bytes.isNotEmpty())
        val back = KpcasVectorFontImporter().importFont(bytes)
        assertTrue(back.isNotEmpty())

        val baos = ByteArrayOutputStream()
        KpcasVectorFontExporter().exportFontToStream(vf, baos)
        KpcasVectorFontImporter().importFont(ByteArrayInputStream(baos.toByteArray()))

        val tmp = File.createTempFile("kpcas-", ".kpcas")
        try {
            KpcasVectorFontExporter().exportFontToFile(vf, tmp)
            KpcasVectorFontImporter().importFont(tmp)
        } finally { tmp.delete() }
    }

    @Test
    fun kpcax_vector_round_trip() {
        val vf = buildSmallVectorFont()
        val bytes = KpcaxVectorFontExporter().exportFontToBytes(vf)
        assertTrue(bytes.isNotEmpty())
        val back = KpcaxVectorFontImporter().importFont(bytes)
        assertTrue(back.isNotEmpty())

        val baos = ByteArrayOutputStream()
        KpcaxVectorFontExporter().exportFontToStream(vf, baos)
        KpcaxVectorFontImporter().importFont(ByteArrayInputStream(baos.toByteArray()))

        val tmp = File.createTempFile("kpcax-", ".kpcax")
        try {
            KpcaxVectorFontExporter().exportFontToFile(vf, tmp)
            KpcaxVectorFontImporter().importFont(tmp)
        } finally { tmp.delete() }
    }

    @Test
    fun amiga_contents_file_round_trip() {
        val bm = TestFonts.tinyFont()
        val exporter = AmigaBitmapFontExporter.ContentsFile(false)
        val bytes = exporter.exportFontToBytes(bm)
        assertTrue(bytes.isNotEmpty())
        val baos = ByteArrayOutputStream()
        exporter.exportFontToStream(bm, baos)

        // File export creates a .font contents file + nested directory with descriptor.
        val dir = File.createTempFile("amiga-", ".dir")
        dir.delete(); dir.mkdir()
        try {
            val contents = File(dir, "Amiga.font")
            exporter.exportFontToFile(bm, contents)
            AmigaBitmapFontImporter.ContentsFile().importFont(contents)
        } finally {
            dir.listFiles()?.forEach { if (it.isDirectory) it.listFiles()?.forEach { f -> f.delete() }; it.delete() }
            dir.delete()
        }
    }
}
