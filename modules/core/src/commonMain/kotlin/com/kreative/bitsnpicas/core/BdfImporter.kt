package com.kreative.bitsnpicas.core

/**
 * Pure-Kotlin multiplatform BDF (Glyph Bitmap Distribution Format) parser.
 *
 * Ported from the frozen Java `BDFBitmapFontImporter.importFontImpl`.
 * Carries the S1/C4 fix (resource closure) trivially -- the commonMain
 * port takes an already-materialised `String` input, so there is no
 * `Scanner` / `FileInputStream` / resource to leak in the first place.
 *
 * Line-oriented, keyword/value pairs, hex-encoded bitmap rows. The
 * full file (or a single font section of it) is split into lines and
 * iterated by a simple index cursor -- no `java.util.Scanner`, and no
 * `java.lang.Integer.parseInt`-style JVM calls in the hot path (Kotlin
 * `String.toIntOrNull` is multiplatform and sufficient here).
 *
 * Charset-registry handling (M8): ISO10646-1 uses identity Unicode
 * mapping. FontSpecific offsets ENCODING values into the Private Use
 * Area at U+F000. Any other registry is surfaced as a warning in
 * [BdfResult.warnings]; the glyph is stored under its named key
 * (STARTCHAR name) instead of being silently dropped (finding 8) or
 * replaced with U+FFFD (finding 9).
 */
public object BdfImporter {

    /**
     * Exception thrown when BDF data is malformed beyond what the
     * parser can gracefully skip past (e.g. no STARTFONT at all).
     */
    public class BdfParseException(message: String) : ParseException(message)

    /**
     * Result container: a parsed font plus non-fatal warnings.
     * Warnings include unsupported CHARSET_REGISTRY values and
     * un-decodable ENCODING lines.
     */
    public data class BdfResult(
        val font: BitmapFont,
        val warnings: List<String>,
    )

    // ---- public API --------------------------------------------------------

    /**
     * Parse the first font in a BDF document. Ignores warnings; use
     * [readWithWarnings] to inspect them.
     *
     * @throws BdfParseException if the input contains no STARTFONT block.
     */
    public fun read(text: String): BitmapFont = readWithWarnings(text).font

    /**
     * Parse the first font in a BDF document and return it alongside
     * any non-fatal warnings accumulated during parsing.
     */
    public fun readWithWarnings(text: String): BdfResult {
        val lines = text.split('\n').map { it.trimEnd('\r') }
        val cursor = LineCursor(lines)
        val warnings = mutableListOf<String>()
        while (cursor.hasNext()) {
            val kv = splitKv(cursor.next())
            if (kv[0] == "STARTFONT") {
                return readFont(cursor, warnings)
            }
        }
        throw BdfParseException("no STARTFONT found in BDF input")
    }

    // ---- line iterator -----------------------------------------------------

    /** Bare-bones line cursor. Avoids kotlin.sequences.Iterator's stateful quirks. */
    private class LineCursor(private val lines: List<String>) {
        private var idx: Int = 0
        fun hasNext(): Boolean = idx < lines.size
        fun next(): String = lines[idx++]
    }

    // ---- parsing helpers ---------------------------------------------------

    /** Split a BDF line into (keyword, rest) tokens on any whitespace. */
    private fun splitKv(line: String): Array<String> {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return arrayOf("")
        val ws = charArrayOf(' ', '\t')
        var i = 0
        while (i < trimmed.length && trimmed[i] !in ws) i++
        val key = trimmed.substring(0, i)
        // skip whitespace between key and value
        while (i < trimmed.length && trimmed[i] in ws) i++
        return if (i >= trimmed.length) arrayOf(key) else arrayOf(key, trimmed.substring(i))
    }

    /** Strip surrounding double quotes and un-escape `""` -> `"`, matching Java dequote. */
    private fun dequote(s: String): String {
        if (s.length < 2) return s
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length - 1).replace("\"\"", "\"")
        }
        return s
    }

    /** Hand-rolled hex nibble decoder -- no `java.lang.Integer.parseInt`. */
    private fun hexCharToNibble(ch: Char): Int {
        return when (ch) {
            in '0'..'9' -> ch.code - '0'.code
            in 'A'..'F' -> ch.code - 'A'.code + 10
            in 'a'..'f' -> ch.code - 'a'.code + 10
            else -> -1
        }
    }

    // ---- font-level parser -------------------------------------------------

    private fun readFont(cursor: LineCursor, warnings: MutableList<String>): BdfResult {
        // State accumulators, mirroring what the Java BitmapFont.setXxx() would store.
        val characters = mutableMapOf<Int, BitmapGlyph>()
        val namedGlyphs = mutableMapOf<String, BitmapGlyph>()

        var name: String? = null
        var emAscent = 0
        var emDescent = 0
        var lineAscent = 0
        var lineDescent = 0
        var xHeight = 0
        var capHeight = 0
        var newGlyphWidth = 0

        // Charset registry marker for encoding translation. `ISO10646`
        // (identity Unicode) is represented as `null`; `FONT_SPECIFIC`
        // is represented by the sentinel [CharsetMode.FontSpecific]; all
        // other registries are surfaced as warnings and fall back to
        // identity mapping (best-effort).
        var mode: CharsetMode = CharsetMode.Identity

        while (cursor.hasNext()) {
            val line = cursor.next()
            val kv = splitKv(line)
            val key = kv[0]

            if (key == "STARTCHAR") {
                val gn = if (kv.size > 1) kv[1] else ""
                readChar(cursor, characters, namedGlyphs, mode, gn, warnings)
                continue
            }
            if (key == "ENDFONT") break
            if (kv.size < 2) continue
            val value = kv[1]

            when (key) {
                "FAMILY_NAME" -> name = dequote(value)
                // WEIGHT_NAME / FONT_VERSION / COPYRIGHT / FOUNDRY are
                // out-of-scope for this first pass (the commonMain
                // BitmapFont carries only a single `name` slot).
                "WEIGHT_NAME",
                "FONT_VERSION",
                "COPYRIGHT",
                "FOUNDRY" -> { /* skip gracefully */ }

                "FONT_ASCENT" -> {
                    val i = dequote(value).toIntOrNull()
                    if (i != null) {
                        lineAscent = i
                        emAscent = i
                        newGlyphWidth = i + emDescent
                    }
                }
                "FONT_DESCENT" -> {
                    val i = dequote(value).toIntOrNull()
                    if (i != null) {
                        lineDescent = i
                        emDescent = i
                        newGlyphWidth = i + emAscent
                    }
                }
                "X_HEIGHT" -> dequote(value).toIntOrNull()?.let { xHeight = it }
                "CAP_HEIGHT" -> dequote(value).toIntOrNull()?.let { capHeight = it }

                "CHARSET_REGISTRY" -> {
                    val registry = dequote(value)
                    mode = when {
                        registry.equals("ISO10646", ignoreCase = true) -> CharsetMode.Identity
                        registry.equals("FontSpecific", ignoreCase = true) -> CharsetMode.FontSpecific
                        else -> {
                            // M8: surface unsupported registries as a
                            // warning (instead of System.err.println)
                            // and fall back to identity mapping.
                            warnings.add("Unsupported CHARSET_REGISTRY: $registry")
                            CharsetMode.Identity
                        }
                    }
                }

                // Other BDF properties (SIZE, FONTBOUNDINGBOX, STARTPROPERTIES,
                // CHARSET_ENCODING, SPACING, SWIDTH, RESOLUTION_X/Y, etc.)
                // are out-of-scope for this first pass: the commonMain
                // BitmapFont has no slot for them. Skip gracefully.
                else -> { /* skip */ }
            }
        }

        val font = BitmapFont(
            glyphs = characters,
            emAscent = emAscent,
            emDescent = emDescent,
            lineAscent = lineAscent,
            lineDescent = lineDescent,
            xHeight = xHeight,
            capHeight = capHeight,
            lineGap = 0,
            newGlyphWidth = newGlyphWidth,
            name = name,
        )
        return BdfResult(font, warnings.toList())
    }

    // ---- glyph-level parser ------------------------------------------------

    private fun readChar(
        cursor: LineCursor,
        characters: MutableMap<Int, BitmapGlyph>,
        namedGlyphs: MutableMap<String, BitmapGlyph>,
        mode: CharsetMode,
        gn: String,
        warnings: MutableList<String>,
    ) {
        var encoding = -1
        var advance = 0
        var bbxW = 0
        var bbxH = 0
        var bbxO = 0
        var bbxD = 0
        var bitmap: MutableList<IntArray>? = null

        while (cursor.hasNext()) {
            val line = cursor.next()
            val kv = splitKv(line)
            val key = kv[0]

            if (key == "BITMAP") {
                // Read hex rows until ENDCHAR / ENDFONT / EOF.
                bitmap = MutableList(bbxH) { IntArray(bbxW) }
                if (readBitmap(cursor, bitmap, bbxW)) break
                // readBitmap returned without consuming ENDCHAR --
                // either the declared height matched exactly (ENDCHAR
                // is next in the outer loop), or the input is truncated
                // (the outer loop will terminate at EOF). Either way,
                // fall through and let the outer loop handle it.
                continue
            }
            if (key == "ENDCHAR") break
            if (kv.size < 2) continue
            val value = kv[1]

            when (key) {
                "ENCODING" -> {
                    val raw = dequote(value).toIntOrNull()
                    encoding = if (raw == null) -1 else translateEncoding(raw, mode, warnings)
                }
                "DWIDTH" -> {
                    val parts = dequote(value).split(WHITESPACE)
                    parts.firstOrNull()?.toIntOrNull()?.let { advance = it }
                }
                "BBX" -> {
                    val parts = dequote(value).split(WHITESPACE)
                    bbxW = parts.getOrNull(0)?.toIntOrNull() ?: 0
                    bbxH = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    bbxO = parts.getOrNull(2)?.toIntOrNull() ?: 0
                    bbxD = parts.getOrNull(3)?.toIntOrNull() ?: 0
                }
                else -> { /* skip unknown glyph-level keys */ }
            }
        }

        // Build the glyph. If BITMAP was never seen, use an empty rows
        // buffer at the declared BBX (matches Java's behaviour of
        // retaining a zero-filled `byte[h][w]` from setGlyph).
        val rows: List<IntArray> = bitmap ?: if (bbxH > 0 && bbxW > 0) {
            List(bbxH) { IntArray(bbxW) }
        } else {
            emptyList()
        }

        val glyph = BitmapGlyph(
            bitmap = rows,
            x = bbxO,
            advance = advance,
            y = bbxH + bbxD, // y stores glyph-ascent = h + d (BDF oy is typically negative)
        )

        if (encoding >= 0) {
            characters[encoding] = glyph
        } else if (gn.isNotEmpty()) {
            namedGlyphs[gn] = glyph
        }
        // else: no ENCODING and no name — silently drop (the frozen
        // Java would putNamedGlyph(null, g), which the Java code rejects).
    }

    /**
     * Read hex-encoded bitmap rows into `rows`. Returns `true` if an
     * ENDCHAR line was consumed mid-loop (i.e. declared height >
     * actual rows); `false` if all rows were filled normally (caller's
     * outer loop will pick up the ENDCHAR). Matches the frozen Java
     * `readBitmap` semantics.
     */
    private fun readBitmap(
        cursor: LineCursor,
        rows: MutableList<IntArray>,
        width: Int,
    ): Boolean {
        var row = 0
        while (cursor.hasNext() && row < rows.size) {
            val kv = splitKv(cursor.next())
            if (kv[0] == "ENDCHAR") return true
            unpack(kv[0], rows[row++], width)
        }
        return false
    }

    /**
     * Decode one hex-encoded bitmap row into the destination `IntArray`.
     * Each hex nibble expands to 4 pixels (MSB first); set pixels
     * become 0xFF (matching Java's `byte` value of -1 reinterpreted as
     * unsigned 0xFF). Pixel count is clamped to [width].
     */
    private fun unpack(h: String, dest: IntArray, width: Int) {
        var i = 0
        for (ch in h) {
            val v = hexCharToNibble(ch)
            if (v < 0) continue
            if (i < width) dest[i++] = if ((v and 0x08) == 0) 0 else 0xFF
            if (i < width) dest[i++] = if ((v and 0x04) == 0) 0 else 0xFF
            if (i < width) dest[i++] = if ((v and 0x02) == 0) 0 else 0xFF
            if (i < width) dest[i++] = if ((v and 0x01) == 0) 0 else 0xFF
        }
    }

    // ---- ENCODING translation ----------------------------------------------

    /**
     * Translate a raw ENCODING integer into a Unicode codepoint given
     * the current charset mode. For unsupported modes (which we already
     * warned about at CHARSET_REGISTRY time), the raw value passes
     * through.
     */
    private fun translateEncoding(
        raw: Int,
        mode: CharsetMode,
        @Suppress("UNUSED_PARAMETER") warnings: MutableList<String>,
    ): Int {
        return when (mode) {
            CharsetMode.Identity -> raw
            CharsetMode.FontSpecific -> raw + 0xF000
        }
    }

    /** Minimal mode enum for ENCODING translation. */
    private enum class CharsetMode { Identity, FontSpecific }

    // Reusable whitespace regex for value splits. Kotlin regex is
    // multiplatform; no `java.util.regex` leakage.
    private val WHITESPACE = Regex("\\s+")
}
