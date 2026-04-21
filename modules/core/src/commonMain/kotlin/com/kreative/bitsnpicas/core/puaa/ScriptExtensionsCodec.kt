package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `ScriptExtensions.txt`.
 *
 * Format: `XXXX..YYYY ; Sc1 Sc2 Sc3`
 *
 * Ported from the frozen Java `ScriptExtensionsCodec`.
 */
public class ScriptExtensionsCodec : PuaaCodec {

    override val fileName: String = "ScriptExtensions.txt"
    override val propertyNames: List<String> = listOf("Script_Extensions")

    override fun compile(table: MutablePuaaTable, lines: Sequence<String>) {
        val values = sortedMapOf<String, MutableMap<Int, String>>()
        for (line in lines) {
            val fields = PuaaTextUtility.splitLine(line) ?: continue
            if (fields.size < 2) continue
            try {
                val r = PuaaTextUtility.splitRange(fields[0])
                for (s in fields[1].trim().split(Regex("\\s+"))) {
                    val m = values.getOrPut(s) { sortedMapOf() }
                    for (cp in r[0]..r[1]) m[cp] = s
                }
            } catch (_: NumberFormatException) {
                // skip malformed lines
            }
        }
        val st = table.getOrCreateSubtable("Script_Extensions")
        for ((_, m) in values) {
            st.addAll(PuaaTextUtility.createEntriesFromStringMap(m))
        }
    }

    override fun decompile(table: PuaaTable): List<String> {
        val entries = table.subtables.firstOrNull { it.property == "Script_Extensions" }
            ?: return emptyList()
        if (entries.entries.isEmpty()) return emptyList()

        // Collect per-codepoint script sets.
        val scripts = sortedMapOf<Int, MutableSet<String>>()
        for (e in entries.entries) {
            for (cp in e.firstCodePoint..e.lastCodePoint) {
                val value = e.getPropertyValue(cp) ?: continue
                val scr = scripts.getOrPut(cp) { sortedSetOf() }
                for (s in value.trim().split(Regex("\\s+"))) scr.add(s)
            }
        }

        // Create runs from the combined script strings.
        val runs = mutableListOf<PuaaEntry.Single>()
        for ((cp, scr) in scripts) {
            val value = scr.joinToString(" ")
            runs.add(PuaaEntry.Single(cp, cp, value))
        }
        val collapsed = PuaaTextUtility.createRunsFromEntries(runs)

        // Sort: by length of value string, then case-insensitive value, then code point.
        val sorted = collapsed.sortedWith(Comparator { a, b ->
            val la = (a.value ?: "").length
            val lb = (b.value ?: "").length
            if (la != lb) return@Comparator la.compareTo(lb)
            val cmp = (a.value ?: "").compareTo(b.value ?: "", ignoreCase = true)
            if (cmp != 0) return@Comparator cmp
            if (a.firstCodePoint != b.firstCodePoint) return@Comparator a.firstCodePoint.compareTo(b.firstCodePoint)
            a.lastCodePoint.compareTo(b.lastCodePoint)
        })

        return sorted.map { e ->
            val sb = StringBuilder()
            sb.append(PuaaTextUtility.joinRange(e.firstCodePoint, e.lastCodePoint))
            while (sb.length < 14) sb.append(' ')
            sb.append("; ")
            sb.append(e.value ?: "")
            sb.toString()
        }
    }
}
