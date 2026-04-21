package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `HangulSyllableType.txt`.
 *
 * Format: `XXXX..YYYY ; HST_Value`
 *
 * Ported from the frozen Java `HangulSyllableTypeCodec`.
 */
public class HangulSyllableTypeCodec : PuaaCodec {

    override val fileName: String = "HangulSyllableType.txt"
    override val propertyNames: List<String> = listOf("Hangul_Syllable_Type")

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
        val st = table.getOrCreateSubtable("Hangul_Syllable_Type")
        st.addAll(PuaaTextUtility.createEntriesFromStringMap(values))
    }

    override fun decompile(table: PuaaTable): List<String> {
        val st = table.subtables.firstOrNull { it.property == "Hangul_Syllable_Type" }
            ?: return emptyList()
        if (st.entries.isEmpty()) return emptyList()

        val runs = PuaaTextUtility.createRunsFromEntries(st.entries)

        // Find first code point of each type.
        val firstOfType = mutableMapOf<String, Int>()
        for (e in runs) {
            val v = e.value ?: continue
            val existing = firstOfType[v]
            if (existing == null || e.firstCodePoint < existing) {
                firstOfType[v] = e.firstCodePoint
            }
        }

        val sorted = runs.sortedWith(Comparator { a, b ->
            val foA = firstOfType[a.value] ?: 0
            val foB = firstOfType[b.value] ?: 0
            if (foA != foB) return@Comparator foA.compareTo(foB)
            val cmp = (a.value ?: "").compareTo(b.value ?: "")
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
