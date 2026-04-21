package com.kreative.bitsnpicas.core.puaa

/**
 * Base class for category codecs that map code-point ranges to a single
 * enumerated property value (e.g. `Grapheme_Cluster_Break`, `Word_Break`).
 *
 * Format: `XXXX..YYYY ; Category_Value`
 *
 * Ported from the frozen Java `AbstractCategoryCodec`.
 */
public open class CategoryCodec(
    override val fileName: String,
    private val propName: String,
    private val propValues: List<String>,
) : PuaaCodec {

    override val propertyNames: List<String> = listOf(propName)

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
        val st = table.getOrCreateSubtable(propName)
        st.addAll(PuaaTextUtility.createEntriesFromStringMap(values))
    }

    override fun decompile(table: PuaaTable): List<String> {
        val st = table.subtables.firstOrNull { it.property == propName }
            ?: return emptyList()
        if (st.entries.isEmpty()) return emptyList()

        val runs = PuaaTextUtility.createRunsFromEntries(st.entries)

        val sorted = runs.sortedWith(Comparator { a, b ->
            val va = propValues.indexOf(a.value ?: "")
            val vb = propValues.indexOf(b.value ?: "")
            if (va != vb) return@Comparator va.compareTo(vb)
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
