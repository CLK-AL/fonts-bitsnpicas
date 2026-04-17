package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `Scripts.txt`.
 *
 * Format: `XXXX..YYYY ; Script_Name`
 *
 * Ported from the frozen Java `com.kreative.bitsnpicas.puaa.ScriptsCodec`.
 */
public class ScriptsCodec : PuaaCodec {

    override val fileName: String = "Scripts.txt"
    override val propertyNames: List<String> = listOf("Script")

    override fun compile(table: MutablePuaaTable, lines: Sequence<String>) {
        val values = mutableMapOf<Int, String>()
        for (line in lines) {
            val fields = PuaaTextUtility.splitLine(line) ?: continue
            if (fields.size < 2) continue
            try {
                val r = PuaaTextUtility.splitRange(fields[0])
                val v = fields[1].trim()
                for (cp in r[0]..r[1]) values[cp] = v
            } catch (_: NumberFormatException) {
                // skip malformed lines
            }
        }

        val st = table.getOrCreateSubtable("Script")
        st.addAll(PuaaTextUtility.createEntriesFromStringMap(values))
    }

    override fun decompile(table: PuaaTable): List<String> {
        val st = table.subtables.firstOrNull { it.property == "Script" }
            ?: return emptyList()
        if (st.entries.isEmpty()) return emptyList()

        val runs = PuaaTextUtility.createRunsFromEntries(st.entries)

        // Find the first code point in each script for sorting.
        val firstOfScript = mutableMapOf<String, Int>()
        for (e in runs) {
            val v = e.value ?: continue
            val existing = firstOfScript[v]
            if (existing == null || e.firstCodePoint < existing) {
                firstOfScript[v] = e.firstCodePoint
            }
        }

        val sorted = runs.sortedWith(Comparator { a, b ->
            val foA = firstOfScript[a.value] ?: 0
            val foB = firstOfScript[b.value] ?: 0
            if (foA != foB) return@Comparator foA.compareTo(foB)
            val cmp = (a.value ?: "").compareTo(b.value ?: "")
            if (cmp != 0) return@Comparator cmp
            if (a.firstCodePoint != b.firstCodePoint) return@Comparator a.firstCodePoint.compareTo(b.firstCodePoint)
            a.lastCodePoint.compareTo(b.lastCodePoint)
        })

        val result = mutableListOf<String>()
        for (e in sorted) {
            val sb = StringBuilder()
            sb.append(PuaaTextUtility.joinRange(e.firstCodePoint, e.lastCodePoint))
            while (sb.length < 14) sb.append(' ')
            sb.append("; ")
            sb.append(e.value ?: "")
            result.add(sb.toString())
        }
        return result
    }
}
