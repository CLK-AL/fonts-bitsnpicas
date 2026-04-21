package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `DerivedAge.txt`.
 *
 * Format: `XXXX..YYYY ; VersionString`
 *
 * Ported from the frozen Java `DerivedAgeCodec`.
 */
public class DerivedAgeCodec : PuaaCodec {

    override val fileName: String = "DerivedAge.txt"
    override val propertyNames: List<String> = listOf("Age")

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
        val st = table.getOrCreateSubtable("Age")
        st.addAll(PuaaTextUtility.createEntriesFromStringMap(values))
    }

    override fun decompile(table: PuaaTable): List<String> {
        val st = table.subtables.firstOrNull { it.property == "Age" }
            ?: return emptyList()
        if (st.entries.isEmpty()) return emptyList()

        val runs = PuaaTextUtility.createRunsFromEntries(st.entries)
        val sorted = runs.sortedWith(Comparator { a, b ->
            val cmp = PuaaTextUtility.naturalCompare(a.value ?: "", b.value ?: "")
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
