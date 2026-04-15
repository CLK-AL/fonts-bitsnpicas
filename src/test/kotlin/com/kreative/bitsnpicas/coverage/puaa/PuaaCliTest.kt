package com.kreative.bitsnpicas.coverage.puaa

import com.kreative.bitsnpicas.exporter.TTFBitmapFontExporter
import com.kreative.bitsnpicas.coverage.TestFonts
import com.kreative.bitsnpicas.puaa.PuaaCodecTest
import com.kreative.bitsnpicas.puaa.PuaaCompiler
import com.kreative.bitsnpicas.puaa.PuaaDecompiler
import com.kreative.bitsnpicas.puaa.PuaaLookup
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Path
import kotlin.test.assertTrue

/**
 * Drive the puaa CLI tools (PuaaCompiler / PuaaDecompiler / PuaaLookup /
 * PuaaCodecTest) end-to-end:
 *
 *   1. Build a fresh TTF in a temp directory.
 *   2. Drop a small UCD-style data file (e.g. NameAliases.txt) into the
 *      temp directory.
 *   3. Run PuaaCompiler with -d data.txt -i font.ttf -o font.ttf to inject
 *      a PUAA table into the font.
 *   4. Run PuaaDecompiler to round-trip the PUAA table back to a UCD-style
 *      file in another temp dir.
 *   5. Run PuaaLookup against the modified font with various flag
 *      combinations to drive its main() branches.
 *   6. Run PuaaCodecTest pointing at the original UCD data dir.
 */
class PuaaCliTest {

    private fun captureStdout(block: () -> Unit): String {
        val orig = System.out
        val buf = ByteArrayOutputStream()
        System.setOut(PrintStream(buf))
        try {
            block()
        } finally {
            System.setOut(orig)
        }
        return buf.toString()
    }

    private fun captureStderr(block: () -> Unit): String {
        val orig = System.err
        val buf = ByteArrayOutputStream()
        System.setErr(PrintStream(buf))
        try {
            block()
        } finally {
            System.setErr(orig)
        }
        return buf.toString()
    }

    @Test
    fun puaaCompiler_decompiler_lookup_round_trip(@TempDir tmp: Path) {
        // 1) Write a synthetic TTF bitmap font.
        val ttf = tmp.resolve("font.ttf").toFile()
        ttf.writeBytes(TTFBitmapFontExporter().exportFontToBytes(TestFonts.tinyFont()))
        assertTrue(ttf.length() > 0)

        // 2) Drop a tiny UCD data file (NameAliases.txt) in the temp dir.
        val data = tmp.resolve("NameAliases.txt").toFile()
        data.writeText(
            """
            # NameAliases
            E000;PRIVATE USE ALPHA;alternate
            E001;PRIVATE USE BETA;abbreviation
            """.trimIndent()
        )

        // Also add UnicodeData.txt so we exercise multiple codecs.
        val ud = tmp.resolve("UnicodeData.txt").toFile()
        ud.writeText("""
            E000;PRIVATE USE ALPHA;Co;0;L;;;;;N;;;;;
            E001;PRIVATE USE BETA;Co;0;L;;;;;N;;;;;
        """.trimIndent())

        val outFont = tmp.resolve("font-with-puaa.ttf").toFile()

        // 3) Compile - inject PUAA into the TTF.
        val compileOut = captureStdout {
            PuaaCompiler.main(arrayOf(
                "-d", data.absolutePath,
                "-d", ud.absolutePath,
                "-i", ttf.absolutePath,
                "-o", outFont.absolutePath,
            ))
        }
        assertTrue(outFont.exists() && outFont.length() > 0, "PuaaCompiler did not produce output: $compileOut")
        assertTrue(compileOut.contains("DONE"))

        // Drive the "single inputFile, multiple outputFiles" branch and the
        // "multiple inputFiles, single outputFile" branch by invoking variants.
        val outFont2 = tmp.resolve("font2.ttf").toFile()
        val outFont3 = tmp.resolve("font3.ttf").toFile()
        captureStdout {
            // Two outputs, one input -> falls into the (in==1, out!=1) and (in!=1, out==1) sequences.
            PuaaCompiler.main(arrayOf(
                "-d", data.absolutePath,
                "-i", ttf.absolutePath,
                "-o", outFont2.absolutePath,
                "-o", outFont3.absolutePath,
            ))
        }

        // Use the directory-as-data-source branch and the no-input/no-output
        // default-puaa.out branch (write into tmp via cwd jump? skip - cwd
        // change is test-unfriendly. Just use a passing directory as -d.)
        captureStdout {
            PuaaCompiler.main(arrayOf("-d", tmp.toFile().absolutePath, "-i", ttf.absolutePath, "-o", outFont.absolutePath))
        }

        // -D / -I / -O default-list switches.
        captureStdout {
            PuaaCompiler.main(arrayOf(
                "-D", data.absolutePath,
                "-I", ttf.absolutePath,
                "-O", outFont.absolutePath,
            ))
        }

        // -- terminator + unknown option branches.
        captureStderr {
            PuaaCompiler.main(arrayOf("--bogus"))
        }
        captureStdout {
            PuaaCompiler.main(arrayOf("-d", data.absolutePath, "-i", ttf.absolutePath, "--", outFont.absolutePath))
        }

        // 4) Decompile to a directory.
        val dumpDir = tmp.resolve("dump").toFile()
        captureStdout {
            PuaaDecompiler.main(arrayOf("-i", outFont.absolutePath, "-o", dumpDir.absolutePath))
        }
        assertTrue(dumpDir.exists())

        // Multiple inputs, single output dir.
        val dumpDir2 = tmp.resolve("dump2").toFile()
        captureStdout {
            PuaaDecompiler.main(arrayOf(
                "-i", outFont.absolutePath,
                "-i", outFont.absolutePath,
                "-o", dumpDir2.absolutePath,
            ))
        }

        // Single input, multiple outputs.
        val dumpDir3 = tmp.resolve("dump3").toFile()
        val dumpDir4 = tmp.resolve("dump4").toFile()
        captureStdout {
            PuaaDecompiler.main(arrayOf(
                "-i", outFont.absolutePath,
                "-o", dumpDir3.absolutePath,
                "-o", dumpDir4.absolutePath,
            ))
        }

        // -I / -O / -- / unknown.
        captureStderr {
            PuaaDecompiler.main(arrayOf("--bogus"))
        }
        captureStdout {
            PuaaDecompiler.main(arrayOf("-I", outFont.absolutePath, "-O", dumpDir.absolutePath))
        }
        // -- terminator: positional arg goes onto the inputFiles list (default).
        // To avoid producing the default "puaa.d" dir in the test cwd, we
        // also pass an explicit -o.
        captureStdout {
            PuaaDecompiler.main(arrayOf(
                "-i", outFont.absolutePath,
                "-o", dumpDir.absolutePath,
                "--", outFont.absolutePath,
            ))
        }

        // 5) Lookup - exercise list-properties, per-property, per-codepoint paths.
        val listOut = captureStdout {
            PuaaLookup.main(arrayOf("-i", outFont.absolutePath))
        }
        assertTrue(listOut.contains("Properties"))

        captureStdout {
            PuaaLookup.main(arrayOf("-i", outFont.absolutePath, "-p", "Name_Alias"))
        }
        captureStdout {
            PuaaLookup.main(arrayOf("-i", outFont.absolutePath, "-c", "U+E000"))
        }
        captureStdout {
            PuaaLookup.main(arrayOf("-i", outFont.absolutePath, "-c", "0xE000"))
        }
        captureStdout {
            PuaaLookup.main(arrayOf("-i", outFont.absolutePath, "-c", "\uE000")) // single-char arg
        }
        captureStderr {
            PuaaLookup.main(arrayOf("-i", outFont.absolutePath, "-c", "not-a-cp"))
        }
        captureStdout {
            // Positional code point after "--" terminator.
            PuaaLookup.main(arrayOf("-i", outFont.absolutePath, "--", "U+E001"))
        }
        captureStderr {
            PuaaLookup.main(arrayOf("-i", outFont.absolutePath, "--unknown-flag"))
        }
        // No -i: tables list ends up empty, early return.
        captureStdout {
            PuaaLookup.main(arrayOf("-c", "U+E000"))
        }

        // Help / no-args paths.
        captureStdout {
            PuaaLookup.main(arrayOf("--help"))
        }
        captureStdout {
            PuaaCompiler.main(arrayOf("--help"))
        }
        captureStdout {
            PuaaDecompiler.main(arrayOf("--help"))
        }

        // 6) PuaaCodecTest sweeping the data dir.
        captureStdout {
            PuaaCodecTest.main(arrayOf(tmp.toFile().absolutePath))
        }
        // And against a single file path.
        captureStdout {
            PuaaCodecTest.main(arrayOf(data.absolutePath))
        }
    }

    @Test
    fun puaaLookup_handles_invalid_input_file_gracefully(@TempDir tmp: Path) {
        // File too small triggers IOException branch in extract().
        val tooSmall = tmp.resolve("tiny.ttf").toFile()
        tooSmall.writeBytes(byteArrayOf(0, 1, 2, 3))
        captureStdout {
            PuaaLookup.main(arrayOf("-i", tooSmall.absolutePath, "-c", "U+E000"))
        }
        captureStdout {
            PuaaCompiler.main(arrayOf("-d", tmp.toFile().absolutePath, "-i", tooSmall.absolutePath, "-o", tooSmall.absolutePath))
        }
        captureStdout {
            PuaaDecompiler.main(arrayOf("-i", tooSmall.absolutePath, "-o", tmp.toFile().absolutePath))
        }
    }
}
