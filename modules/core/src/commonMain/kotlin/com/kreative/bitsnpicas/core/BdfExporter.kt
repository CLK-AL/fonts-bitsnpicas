package com.kreative.bitsnpicas.core

/**
 * Pure-Kotlin multiplatform BDF (Glyph Bitmap Distribution Format) writer.
 *
 * Ported from the frozen Java
 * `com.kreative.bitsnpicas.exporter.BDFBitmapFontExporter.exportFont`,
 * with finding M7 fixed natively: the Java exporter scans
 * `for (int i = 0; i < 0x110000; i++)` on every export -- ~1.1M
 * iterations regardless of font size. The commonMain port iterates
 * [BitmapFont.glyphs] directly, so runtime is O(#glyphs).
 *
 * Output is the canonical BDF text representation: ASCII, line-oriented,
 * keyword/value pairs, hex-encoded bitmap rows padded to byte boundaries.
 * No `java.*` dependency, no `java.lang.Integer.toHexString`; a small
 * hand-rolled hex formatter is used instead so the module can compile
 * against every KMP target (jvm, js, wasm, native).
 *
 * The round-trip [BdfImporter.read] -> [BdfExporter.write] -> [BdfImporter.read]
 * is semantically stable: glyph count, dimensions, advance, offsets, and
 * bitmap bytes survive intact. Some BDF-side properties (SIZE, X_HEIGHT,
 * CAP_HEIGHT, FOUNDRY, WEIGHT_NAME, FONT_VERSION, COPYRIGHT) are
 * emitted from the commonMain [BitmapFont] fields that are populated;
 * fields not represented in the model (foundry, version, copyright) are
 * simply omitted from the output.
 */
public object BdfExporter {

    /**
     * Serialize [font] to BDF text. Iterates [BitmapFont.glyphs] keys
     * in insertion order -- O(#glyphs), never O(0x110000). See M7.
     */
    public fun write(font: BitmapFont): String {
        val sb = StringBuilder()
        val cnt = font.glyphs.size

        // Compute FONTBOUNDINGBOX from glyph metrics (mirrors the Java
        // pass over font.containsCharacter(i) for i in [0, 0x110000)).
        var bbl = 0
        var bbr = 0
        var bbt = 0
        var bbb = 0
        for ((_, g) in font.glyphs) {
            val gOffset = g.x
            val gWidth = g.width
            val gAscent = g.y
            // In the Java model, glyphDescent = -bbxD where bbxD was
            // the BBX y-offset. In commonMain, y = h + bbxD, so
            // descent = height - y.
            val gDescent = g.height - g.y
            if (gOffset < bbl) bbl = gOffset
            if (gOffset + gWidth > bbr) bbr = gOffset + gWidth
            if (gAscent > bbt) bbt = gAscent
            if (gDescent > bbb) bbb = gDescent
        }

        val pointSize = font.emAscent + font.emDescent
        val pixelSize = font.lineAscent + font.lineDescent
        val family = font.name ?: "Unknown"

        sb.append("STARTFONT 2.1").append('\n')
        // FONT XLFD: mirror the Java shape. Foundry/weight/slant slots
        // are filled with placeholders since commonMain BitmapFont has
        // no dedicated fields for them.
        sb.append("FONT -Unknown-")
            .append(family)
            .append("-Medium-R-Normal--")
            .append(pixelSize)
            .append('-')
            .append(pixelSize)
            .append("-75-75-c-80-iso10646-1")
            .append('\n')
        sb.append("SIZE ").append(pixelSize).append(" 75 75").append('\n')
        sb.append("FONTBOUNDINGBOX ")
            .append(bbr - bbl).append(' ')
            .append(bbt + bbb).append(' ')
            .append(bbl).append(' ')
            .append(-bbb)
            .append('\n')

        // STARTPROPERTIES: emit only the subset that commonMain
        // BitmapFont actually models. Foundry/weight/version/copyright
        // are omitted when null.
        val props = buildList {
            if (font.name != null) add("FAMILY_NAME" to enquote(font.name))
            add("FONT_ASCENT" to font.lineAscent.toString())
            add("FONT_DESCENT" to font.lineDescent.toString())
            add("POINT_SIZE" to pointSize.toString())
            add("X_HEIGHT" to font.xHeight.toString())
            add("CAP_HEIGHT" to font.capHeight.toString())
            add("CHARSET_REGISTRY" to enquote("ISO10646"))
            add("CHARSET_ENCODING" to enquote("1"))
        }
        sb.append("STARTPROPERTIES ").append(props.size).append('\n')
        for ((k, v) in props) {
            sb.append(k).append(' ').append(v).append('\n')
        }
        sb.append("ENDPROPERTIES").append('\n')

        sb.append("CHARS ").append(cnt).append('\n')
        // M7: iterate the glyph map, NEVER 0..0x110000.
        for ((cp, g) in font.glyphs) {
            writeChar(sb, g, cp, bbl, bbr)
        }
        sb.append("ENDFONT").append('\n')
        return sb.toString()
    }

    /** Write one STARTCHAR..ENDCHAR block. */
    private fun writeChar(
        sb: StringBuilder,
        g: BitmapGlyph,
        codepoint: Int,
        bbl: Int,
        bbr: Int,
    ) {
        val name = "U+" + toHex(codepoint, 4).uppercase()
        sb.append("STARTCHAR ").append(name).append('\n')
        sb.append("ENCODING ").append(codepoint).append('\n')
        val denom = bbr - bbl
        val swidth = if (denom == 0) 0 else 1000 * g.advance / denom
        sb.append("SWIDTH ").append(swidth).append(" 0").append('\n')
        sb.append("DWIDTH ").append(g.advance).append(" 0").append('\n')
        val descent = g.height - g.y
        sb.append("BBX ")
            .append(g.width).append(' ')
            .append(g.height).append(' ')
            .append(g.x).append(' ')
            .append(-descent)
            .append('\n')
        sb.append("BITMAP").append('\n')
        for (row in g.bitmap) {
            appendHexRow(sb, row)
            sb.append('\n')
        }
        // Java writes "00" for a zero-height glyph whose BITMAP block
        // would otherwise be empty -- keep parity.
        if (g.bitmap.isEmpty()) {
            sb.append("00").append('\n')
        }
        sb.append("ENDCHAR").append('\n')
    }

    /**
     * Append one hex-encoded bitmap row. Each group of 8 pixels becomes
     * one byte, MSB-first; the byte is emitted as two uppercase hex
     * digits. A zero-width row yields "00" (parity with the Java
     * exporter's `if (s.length() == 0) s.append("00")`).
     */
    private fun appendHexRow(sb: StringBuilder, row: IntArray) {
        if (row.isEmpty()) {
            sb.append("00")
            return
        }
        var col = 0
        while (col < row.size) {
            var b = 0
            for (c in 0 until 8) {
                b = b shl 1
                // The Java exporter treats any pixel byte with its high
                // bit set (signed < 0, i.e. >= 0x80) as "on". We match:
                // `row[col+c] and 0x80 != 0` -- this keeps parity with
                // the decoded bitmap bytes (0xFF for set, 0x00 for
                // cleared) that BdfImporter produces.
                if (col + c < row.size && (row[col + c] and 0x80) != 0) {
                    b = b or 1
                }
            }
            sb.append(hexDigit((b ushr 4) and 0x0F))
            sb.append(hexDigit(b and 0x0F))
            col += 8
        }
    }

    /**
     * BDF property quoting: integers (optionally leading `-`) go
     * verbatim; any other value is double-quoted with embedded `"`
     * doubled to `""`. Mirrors the frozen Java `enquote(String)`.
     */
    private fun enquote(s: String): String {
        if (isInteger(s)) return s
        val escaped = s.replace("\"", "\"\"")
        return "\"" + escaped + "\""
    }

    private fun isInteger(s: String): Boolean {
        if (s.isEmpty()) return false
        var i = 0
        if (s[0] == '-') {
            if (s.length == 1) return false
            i = 1
        }
        while (i < s.length) {
            val c = s[i]
            if (c < '0' || c > '9') return false
            i++
        }
        return true
    }

    /**
     * Hand-rolled hex formatter -- multiplatform safe, no
     * `java.lang.Integer.toHexString`. Produces lowercase by default;
     * call-sites that need uppercase use `.uppercase()`.
     *
     * @param value non-negative integer
     * @param minWidth zero-pad the result up to this width
     */
    private fun toHex(value: Int, minWidth: Int): String {
        if (value == 0) {
            return "0".repeat(maxOf(1, minWidth))
        }
        val buf = StringBuilder()
        var v = value
        // Unsigned right-shift so negative ints (shouldn't happen for
        // codepoints, but be defensive) still produce stable output.
        while (v != 0) {
            buf.append(hexDigit(v and 0x0F))
            v = v ushr 4
        }
        while (buf.length < minWidth) buf.append('0')
        return buf.reverse().toString()
    }

    private fun hexDigit(nibble: Int): Char {
        return if (nibble < 10) ('0' + nibble) else ('A' + (nibble - 10))
    }
}
