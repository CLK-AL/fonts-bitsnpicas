package com.kreative.bitsnpicas.core

/**
 * Pure-Kotlin multiplatform parser for Playdate `.fnt` text metadata.
 *
 * Ported from the text-parsing portion of the frozen Java
 * `PlaydateBitmapFontImporter`. The actual PNG sprite-sheet
 * decoding stays JVM-side; this parser extracts just the metadata
 * fields (tracking, spacing, glyph widths, kern pairs, metrics,
 * cell dimensions, and name) from the `.fnt` text content.
 *
 * The `.fnt` format uses property lines like:
 *   tracking=1
 *   --metrics={"baseline":10,"xHeight":7,"capHeight":9}
 *   width=16
 *   height=16
 *
 * followed by glyph-width lines:
 *   A\t8
 *   space\t4
 *   AB\t-1    (kern pair)
 */
public object PlaydateMetadataParser {

    /**
     * Parsed Playdate `.fnt` metadata.
     *
     * @property name        Font name from the `name` property, or null if absent.
     * @property tracking    Inter-character tracking (default 1).
     * @property cellWidth   Cell width from the `width` property, or null if absent.
     * @property cellHeight  Cell height from the `height` property, or null if absent.
     * @property baseline    Baseline offset from metrics, or null.
     * @property xHeight     x-height from metrics, or null.
     * @property capHeight   Cap height from metrics, or null.
     * @property glyphWidths Ordered list of (codepoints, width) pairs for single-codepoint entries.
     * @property kernPairs   Ordered list of (leftCp, rightCp, width) triples.
     */
    public data class PlaydateMetadata(
        val name: String? = null,
        val tracking: Int = 1,
        val cellWidth: Int? = null,
        val cellHeight: Int? = null,
        val baseline: Int? = null,
        val xHeight: Int? = null,
        val capHeight: Int? = null,
        val glyphWidths: List<Pair<Int, Int>> = emptyList(),
        val kernPairs: List<Triple<Int, Int, Int>> = emptyList(),
    )

    // Matches property lines:  optional "--" prefix, key = value
    private val PROPERTY_LINE = Regex("""^(--\s*)?(\w+)\s*=\s*(.+)$""")
    // Matches JSON-style metric entries:  "key" : value
    private val METRICS_ENTRY = Regex(""""(\w+)"\s*:\s*(-?\d+)""")

    /**
     * Parse the text content of a Playdate `.fnt` file.
     *
     * @param text the full `.fnt` file content.
     * @return parsed [PlaydateMetadata].
     */
    public fun parse(text: String): PlaydateMetadata {
        var name: String? = null
        var tracking = 1
        var cellWidth: Int? = null
        var cellHeight: Int? = null
        var baseline: Int? = null
        var xHeight: Int? = null
        var capHeight: Int? = null
        val glyphWidths = mutableListOf<Pair<Int, Int>>()
        val kernPairs = mutableListOf<Triple<Int, Int, Int>>()

        for (line in text.lineSequence()) {
            val propMatch = PROPERTY_LINE.matchEntire(line)
            if (propMatch != null) {
                val key = propMatch.groupValues[2]
                val value = propMatch.groupValues[3]
                when (key) {
                    "name" -> name = value
                    "tracking" -> tracking = value.toIntOrNull() ?: tracking
                    "width" -> cellWidth = value.toIntOrNull()
                    "height" -> cellHeight = value.toIntOrNull()
                    "metrics" -> {
                        for (entry in METRICS_ENTRY.findAll(value)) {
                            val mKey = entry.groupValues[1]
                            val mVal = entry.groupValues[2].toIntOrNull() ?: continue
                            when (mKey) {
                                "baseline" -> baseline = mVal
                                "xHeight" -> xHeight = mVal
                                "capHeight" -> capHeight = mVal
                            }
                        }
                    }
                    // "data" is PNG base64 — skip in commonMain
                }
                continue
            }

            // Skip comment lines
            if (line.startsWith("--")) continue

            // Glyph-width or kern-pair line: "CHARS<tab>WIDTH" or "CHARS<spaces>WIDTH"
            val fields = if (line.contains('\t')) {
                line.split(Regex("\t+"))
            } else {
                line.split(Regex("\\s+"))
            }
            if (fields.size != 2) continue

            val width = fields[1].toIntOrNull() ?: continue
            val cps = splitCodePoints(fields[0])
            when (cps.size) {
                1 -> glyphWidths.add(cps[0] to width)
                2 -> kernPairs.add(Triple(cps[0], cps[1], width))
            }
        }

        return PlaydateMetadata(
            name = name,
            tracking = tracking,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            baseline = baseline,
            xHeight = xHeight,
            capHeight = capHeight,
            glyphWidths = glyphWidths,
            kernPairs = kernPairs,
        )
    }

    /**
     * Split a Playdate glyph identifier string into codepoints.
     *
     * Recognises:
     *  - "space" literal -> U+0020
     *  - "U+XXXX" hex escapes
     *  - raw characters (including surrogate pairs for supplementary codepoints)
     */
    internal fun splitCodePoints(s: String): List<Int> {
        val cps = mutableListOf<Int>()
        var i = 0
        val n = s.length
        while (i < n) {
            if (s.regionMatches(i, "space", 0, 5, ignoreCase = false)) {
                i += 5
                cps.add(0x20)
            } else if (s.regionMatches(i, "U+", 0, 2, ignoreCase = false)) {
                i += 2
                var cp = 0
                while (i < n) {
                    val d = hexDigitValue(s[i])
                    if (d < 0) break
                    i++
                    cp = (cp shl 4) or d
                }
                cps.add(cp)
            } else {
                val c1 = s[i]
                if (c1.isHighSurrogate() && i + 1 < n && s[i + 1].isLowSurrogate()) {
                    val c2 = s[i + 1]
                    val cp = 0x10000 + ((c1.code - 0xD800) shl 10) + (c2.code - 0xDC00)
                    cps.add(cp)
                    i += 2
                } else {
                    cps.add(c1.code)
                    i++
                }
            }
        }
        return cps
    }

    private fun hexDigitValue(c: Char): Int {
        return when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> -1
        }
    }
}
