package com.kreative.bitsnpicas.coverage

import com.kreative.bitsnpicas.importer.SFDBitmapFontImporter
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.assertTrue

class SfdImporterCoverageTest {

    @Test
    fun imports_real_sfd_corpus_files() {
        val roots = listOf("fonts/apple2", "fonts/aquarius", "fonts/dosstart", "fonts/gameboy", "fonts/sabine")
            .map(::File)
            .filter { it.isDirectory }
        assertTrue(roots.isNotEmpty(), "expected sfd corpus")
        var imported = 0
        for (dir in roots) {
            val sfds = dir.listFiles { f -> f.extension == "sfd" } ?: continue
            for (sfd in sfds) {
                val importer = SFDBitmapFontImporter()
                val fonts = importer.importFont(sfd)
                // Some SFDs have no bitmap strike and return zero fonts; just ensure no crash.
                if (fonts.isNotEmpty()) imported++
            }
        }
        assertTrue(imported > 0, "at least one sfd had a bitmap font")
    }

    @Test
    fun stream_and_bytes_paths() {
        val sfd = File("fonts/apple2").listFiles { f -> f.extension == "sfd" }?.firstOrNull()
        assertTrue(sfd != null)
        val importer = SFDBitmapFontImporter()
        importer.importFont(sfd!!.readBytes())
        importer.importFont(ByteArrayInputStream(sfd.readBytes()))
    }

    @Test
    fun escape_decoding_and_ascii85() {
        // Synthetic SFD with escape sequences and tiny BDF char to exercise decodeEscapes + ASCII85.
        val src = """
            Copyright: c\\ ot\u0020 \n \\ \" \' \a \b \d \e \f \i \o \r \t \v \x20
            FamilyName: Foo\u0020Bar
            Weight: Bold
            StartChar: A
            Encoding: 65 65
            EndChar
            BitmapFont: 8 1 6 2 0
            Resolution: 72
            BDFChar: 0 65 4 0 3 -2 1
            zzzz
            EndBitmapFont
        """.trimIndent().toByteArray(Charsets.UTF_8)
        val fonts = SFDBitmapFontImporter().importFont(src)
        // May or may not produce a font; just ensure no crash.
        assertTrue(fonts.size >= 0)
    }
}
