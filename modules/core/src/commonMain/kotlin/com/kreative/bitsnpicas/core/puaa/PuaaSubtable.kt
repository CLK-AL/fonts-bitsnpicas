package com.kreative.bitsnpicas.core.puaa

/**
 * A named property subtable containing [PuaaEntry] entries.
 * Each subtable corresponds to a single Unicode property
 * (e.g. "General_Category", "Name", etc.).
 */
public data class PuaaSubtable(
    val property: String,
    val entries: List<PuaaEntry>,
) {
    /**
     * Look up the property value for a given code point by scanning
     * all entries and concatenating matching values (matching the
     * frozen Java behaviour for prefix/suffix name entries).
     */
    public fun getPropertyValue(cp: Int): String? {
        var found = false
        val sb = StringBuilder()
        for (entry in entries) {
            if (cp in entry.firstCodePoint..entry.lastCodePoint) {
                val value = entry.getPropertyValue(cp)
                if (value != null) {
                    found = true
                    sb.append(value)
                }
            }
        }
        return if (found) sb.toString() else null
    }
}
