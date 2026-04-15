package com.kreative.bitsnpicas.review

import com.kreative.bitsnpicas.importer.BDFBitmapFontImporter
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertTrue

/**
 * Critical finding C4 - Resource leak: BDF importer never closes Scanner /
 * FileInputStream. The Scanner/Stream stays open forever, exhausting file
 * descriptors under load.
 *
 * After the fix, the three importFont entry points wrap the Scanner (and
 * the underlying stream) in try-with-resources so close() is invoked.
 */
class C4BdfScannerLeakTest {

    /** Minimal but valid BDF body that BDFBitmapFontImporter will accept. */
    private val validBdf = """
        STARTFONT 2.1
        FONT -Test
        SIZE 8 75 75
        FONTBOUNDINGBOX 8 8 0 0
        STARTPROPERTIES 0
        ENDPROPERTIES
        CHARS 0
        ENDFONT
    """.trimIndent().toByteArray(Charsets.UTF_8)

    @Test
    fun importFont_closes_underlying_input_stream() {
        val closeCount = AtomicInteger(0)
        val inner = ByteArrayInputStream(validBdf)
        val counted = object : InputStream() {
            override fun read(): Int = inner.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len)
            override fun close() {
                closeCount.incrementAndGet()
                inner.close()
            }
        }

        BDFBitmapFontImporter().importFont(counted)

        assertTrue(
            closeCount.get() > 0,
            "BDFBitmapFontImporter.importFont(InputStream) must close the stream (C4); closeCount=${closeCount.get()}"
        )
    }
}
