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

    private class MutableSingleRun(var firstCp: Int, var lastCp: Int, val value: String)
}
