package com.kreative.bitsnpicas.core.puaa

/**
 * Text-format utility functions for PUAA codecs.
 *
 * Ported from the frozen Java `PuaaUtility` — only the text-format
 * helpers needed by the codec layer, not the binary-level helpers
 * (which are already in [PuaaTable]).
 */
public object PuaaTextUtility {

    /**
     * Split a property-file line: strip comments (`#`) and trim.
     * Returns null for blank/comment-only lines.
     */
    public fun splitLine(line: String): Array<String>? {
        var s = line
        val commentIdx = s.indexOf('#')
        if (commentIdx >= 0) s = s.substring(0, commentIdx)
        s = s.trim()
        if (s.isEmpty()) return null
        return s.split(';').toTypedArray()
    }

    /**
     * Parse a hex range like "0041" or "0041..005A".
     * Returns `[start, end]`.
     */
    public fun splitRange(range: String): IntArray {
        val parts = range.split(Regex("[.]+"))
        val start = parts[0].trim().toInt(16)
        if (parts.size < 2) return intArrayOf(start, start)
        val end = parts[1].trim().toInt(16)
        return intArrayOf(start, end)
    }

    /**
     * Format a code point as uppercase hex, at least 4 digits.
     */
    public fun toHexString(value: Int): String {
        val s = value.toUInt().toString(16).uppercase()
        return if (s.length >= 4) s else s.padStart(4, '0')
    }

    /**
     * Format a code-point range for output.
     */
    public fun joinRange(firstCodePoint: Int, lastCodePoint: Int): String {
        return if (firstCodePoint == lastCodePoint) {
            toHexString(firstCodePoint)
        } else {
            "${toHexString(firstCodePoint)}..${toHexString(lastCodePoint)}"
        }
    }

    /**
     * Join fields with a delimiter.
     */
    public fun joinLine(fields: Array<String?>, delimiter: String): String {
        val sb = StringBuilder(fields[0] ?: "")
        for (i in 1 until fields.size) {
            sb.append(delimiter)
            if (fields[i] != null) sb.append(fields[i])
        }
        return sb.toString()
    }

    // ---- Entry creation helpers (ported from PuaaUtility) --------------------

    /**
     * From a map of code-point -> string value, create optimised entries
     * (runs of the same value collapsed into range entries).
     */
    public fun createEntriesFromStringMap(map: Map<Int, String>): List<PuaaEntry> {
        val items = map.entries
            .filter { it.value.isNotEmpty() }
            .sortedBy { it.key }

        // Collapse consecutive code points with the same value into runs.
        val runs = mutableListOf<MutableSingleRun>()
        var currentRun: MutableSingleRun? = null
        for ((cp, v) in items) {
            val cr = currentRun
            if (cr != null && cr.lastCp + 1 == cp && cr.value == v) {
                cr.lastCp = cp
            } else {
                val r = MutableSingleRun(cp, cp, v)
                runs.add(r)
                currentRun = r
            }
        }

        return runs.map { PuaaEntry.Single(it.firstCp, it.lastCp, it.value) }
    }

    /**
     * From a map of code-point -> boolean, create Boolean entries.
     */
    public fun createEntriesFromBooleanMap(map: Map<Int, Boolean>): List<PuaaEntry> {
        val items = map.entries
            .filter { it.value != null }
            .sortedBy { it.key }

        val entries = mutableListOf<PuaaEntry>()
        var curFirst = -1
        var curLast = -1
        var curVal = false
        for ((cp, v) in items) {
            if (curFirst >= 0 && curLast + 1 == cp && curVal == v) {
                curLast = cp
            } else {
                if (curFirst >= 0) {
                    entries.add(PuaaEntry.BooleanEntry(curFirst, curLast, curVal))
                }
                curFirst = cp
                curLast = cp
                curVal = v
            }
        }
        if (curFirst >= 0) {
            entries.add(PuaaEntry.BooleanEntry(curFirst, curLast, curVal))
        }
        return entries
    }

    /**
     * From entries (possibly multi-layered), create a flat sorted map
     * of code-point -> string value (concatenating overlapping entries).
     */
    public fun createMapFromEntries(entries: List<PuaaEntry>): Map<Int, String> {
        val map = sortedMapOf<Int, String>()
        for (e in entries) {
            for (cp in e.firstCodePoint..e.lastCodePoint) {
                val value = e.getPropertyValue(cp) ?: continue
                map[cp] = (map[cp] ?: "") + value
            }
        }
        return map
    }

    /**
     * From entries, create a flat list of Single runs (for decompile output).
     */
    public fun createRunsFromEntries(entries: List<PuaaEntry>): List<PuaaEntry.Single> {
        val map = createMapFromEntries(entries)
        val items = map.entries.sortedBy { it.key }

        val runs = mutableListOf<MutableSingleRun>()
        var currentRun: MutableSingleRun? = null
        for ((cp, v) in items) {
            val cr = currentRun
            if (cr != null && cr.lastCp + 1 == cp && cr.value == v) {
                cr.lastCp = cp
            } else {
                val r = MutableSingleRun(cp, cp, v)
                runs.add(r)
                currentRun = r
            }
        }

        return runs.map { PuaaEntry.Single(it.firstCp, it.lastCp, it.value) }
    }

    /**
     * From a map of code-point -> hex integer value, create optimised Hexadecimal/HexMultiple entries.
     * Ported from the frozen Java `PuaaUtility.createEntriesFromHexadecimalMap`.
     */
    public fun createEntriesFromHexadecimalMap(map: Map<Int, Int>): List<PuaaEntry> {
        val items = map.entries.sortedBy { it.key }

        // Create runs of the same hex value.
        class HexRun(var firstCp: Int, var lastCp: Int, val value: Int)

        val runs = mutableListOf<HexRun>()
        var currentRun: HexRun? = null
        for ((cp, v) in items) {
            val cr = currentRun
            if (cr != null && cr.lastCp + 1 == cp && cr.value == v) {
                cr.lastCp = cp
            } else {
                val r = HexRun(cp, cp, v)
                runs.add(r)
                currentRun = r
            }
        }

        // Collapse single-cp runs into HexMultiple entries using intermediate holders.
        class MultiHolder(var firstCp: Int, var lastCp: Int, val values: MutableList<Int>)

        val result = mutableListOf<Any>() // Either PuaaEntry or MultiHolder
        var curMulti: MultiHolder? = null
        for (run in runs) {
            if (run.firstCp != run.lastCp) {
                curMulti = null
                result.add(PuaaEntry.Hexadecimal(run.firstCp, run.lastCp, run.value))
            } else {
                val cm = curMulti
                if (cm != null && cm.lastCp + 1 == run.firstCp) {
                    cm.lastCp = run.firstCp
                    cm.values.add(run.value)
                } else {
                    val nm = MultiHolder(run.firstCp, run.firstCp, mutableListOf(run.value))
                    result.add(nm)
                    curMulti = nm
                }
            }
        }

        // Convert MultiHolder to final entry types.
        return result.map { e ->
            when (e) {
                is MultiHolder -> {
                    if (e.firstCp == e.lastCp) {
                        PuaaEntry.Hexadecimal(e.firstCp, e.lastCp, e.values[0])
                    } else {
                        PuaaEntry.HexMultiple(e.firstCp, e.lastCp, e.values.toIntArray())
                    }
                }
                is PuaaEntry -> e
                else -> error("unexpected")
            }
        }
    }

    /**
     * From a map of code-point -> decimal integer value, create Decimal entries.
     * Ported from the frozen Java `PuaaUtility.createEntriesFromDecimalMap`.
     */
    public fun createEntriesFromDecimalMap(map: Map<Int, Int>): List<PuaaEntry> {
        val items = map.entries.sortedBy { it.key }
        val entries = mutableListOf<PuaaEntry>()
        var curFirst = -1
        var curLast = -1
        var curVal = 0
        for ((cp, v) in items) {
            if (curFirst >= 0 && curLast + 1 == cp && curVal == v) {
                curLast = cp
            } else {
                if (curFirst >= 0) {
                    entries.add(PuaaEntry.Decimal(curFirst, curLast, curVal))
                }
                curFirst = cp
                curLast = cp
                curVal = v
            }
        }
        if (curFirst >= 0) {
            entries.add(PuaaEntry.Decimal(curFirst, curLast, curVal))
        }
        return entries
    }

    /**
     * From a map of code-point -> name string, create optimised entries with
     * prefix/suffix compression. Ported from Java `PuaaUtility.createEntriesFromNameMap`.
     */
    public fun createEntriesFromNameMap(map: Map<Int, String>): List<PuaaEntry> {
        // Sort by code point.
        val items = map.entries
            .filter { it.value.isNotEmpty() }
            .sortedBy { it.key }
            .map { NameItem(it.key, splitName(it.value).toMutableList()) }

        // Prefix compression.
        val prefixes = mutableListOf<PuaaEntry>()
        while (true) {
            val newPrefixes = mutableListOf<PuaaEntry>()
            var o = 0
            while (o < items.size) {
                val first = items[o]
                if (first.pieces.isEmpty()) { o++; continue }
                var firstCp = first.cp
                var lastCp = first.cp
                val prefix = first.pieces.firstOrNull() ?: run { o++; continue }
                var i = o + 1
                while (i < items.size) {
                    val item = items[i]
                    if (lastCp + 1 == item.cp && item.pieces.firstOrNull() == prefix) {
                        lastCp = item.cp
                        i++
                    } else break
                }
                if (firstCp != lastCp) {
                    newPrefixes.add(PuaaEntry.Single(firstCp, lastCp, prefix))
                    for (k in o until i) items[k].pieces.removeFirst()
                }
                o = i
            }
            if (newPrefixes.isEmpty()) break
            prefixes.addAll(newPrefixes)
        }

        // Suffix compression.
        val suffixes = mutableListOf<PuaaEntry>()
        while (true) {
            val newSuffixes = mutableListOf<PuaaEntry>()
            var o = 0
            while (o < items.size) {
                val first = items[o]
                if (first.pieces.isEmpty()) { o++; continue }
                var firstCp = first.cp
                var lastCp = first.cp
                val suffix = first.pieces.lastOrNull() ?: run { o++; continue }
                var i = o + 1
                while (i < items.size) {
                    val item = items[i]
                    if (lastCp + 1 == item.cp && item.pieces.lastOrNull() == suffix) {
                        lastCp = item.cp
                        i++
                    } else break
                }
                if (firstCp != lastCp) {
                    newSuffixes.add(PuaaEntry.Single(firstCp, lastCp, suffix))
                    for (k in o until i) items[k].pieces.removeLast()
                }
                o = i
            }
            if (newSuffixes.isEmpty()) break
            suffixes.addAll(0, newSuffixes)
        }

        // Remainder.
        val remainder1 = mutableMapOf<Int, String>()
        val remainder2 = mutableMapOf<Int, String>()
        for (item in items) {
            if (item.pieces.isEmpty()) continue
            val value = item.pieces.joinToString("")
            val utf8Len = value.encodeToByteArray().size
            if (utf8Len > 255) {
                var h = value.length / 2
                if (h < value.length && value[h].code in 0xDC00..0xDFFF) h++
                remainder1[item.cp] = value.substring(0, h)
                remainder2[item.cp] = value.substring(h)
            } else {
                remainder1[item.cp] = value
            }
        }

        val entries = mutableListOf<PuaaEntry>()
        entries.addAll(prefixes)
        entries.addAll(createEntriesFromStringMap(remainder1))
        entries.addAll(createEntriesFromStringMap(remainder2))
        entries.addAll(suffixes)
        return entries
    }

    /**
     * Natural-order string comparison (numeric substrings compared by value).
     * Ported from Java `PuaaUtility.naturalCompare`.
     */
    public fun naturalCompare(a: String, b: String): Int {
        val na = naturalTokenize(a.trim())
        val nb = naturalTokenize(b.trim())
        for (i in 0 until minOf(na.size, nb.size)) {
            val va = na[i].toDoubleOrNull()
            val vb = nb[i].toDoubleOrNull()
            if (va != null && vb != null) {
                val cmp = va.compareTo(vb)
                if (cmp != 0) return cmp
            } else {
                val cmp = na[i].compareTo(nb[i], ignoreCase = true)
                if (cmp != 0) return cmp
            }
        }
        return na.size - nb.size
    }

    private fun naturalTokenize(s: String): List<String> {
        val tokens = mutableListOf<String>()
        val token = StringBuilder()
        var tokenType = 0
        for (ch in s) {
            val tt = when {
                ch.isDigit() -> 1
                ch.isLetter() -> 2
                else -> 3
            }
            if (tt != tokenType) {
                if (token.isNotEmpty()) {
                    tokens.add(token.toString())
                    token.clear()
                }
                tokenType = tt
            }
            token.append(ch)
        }
        if (token.isNotEmpty()) tokens.add(token.toString())
        return tokens
    }

    private fun isChunky(ch: Char): Boolean {
        return ch.isLetterOrDigit() || ch.isSurrogate() ||
            (ch in '"'..'*') || ch == '<' || ch == '>' || ch == '@' ||
            ch == '[' || ch == ']' || ch == '_' ||
            ch == '{' || ch == '}'
    }

    private fun splitName(name: String): List<String> {
        val pieces = mutableListOf<String>()
        val ch = name.toCharArray()
        var o = 0
        while (o < ch.size) {
            var i = o
            while (i < ch.size && isChunky(ch[i])) i++
            while (i < ch.size && !isChunky(ch[i]) && !ch[i].isWhitespace()) i++
            while (i < ch.size && ch[i].isWhitespace()) i++
            pieces.add(name.substring(o, i))
            o = i
        }
        return pieces
    }

    private class NameItem(val cp: Int, val pieces: MutableList<String>)

    private class MutableSingleRun(var firstCp: Int, var lastCp: Int, val value: String)
}

