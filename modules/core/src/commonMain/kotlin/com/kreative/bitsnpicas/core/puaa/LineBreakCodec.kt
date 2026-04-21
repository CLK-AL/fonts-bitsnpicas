package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `LineBreak.txt`.
 *
 * Format: `XXXX..YYYY;LB_Value`
 *
 * Ported from the frozen Java `AbstractStringCodec` / `LineBreakCodec`.
 */
public class LineBreakCodec : PuaaCodec {

    override val fileName: String = "LineBreak.txt"
    override val propertyNames: List<String> = listOf("Line_Break")

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
        val st = table.getOrCreateSubtable("Line_Break")
        st.addAll(PuaaTextUtility.createEntriesFromStringMap(values))
    }

    override fun decompile(table: PuaaTable): List<String> {
        val st = table.subtables.firstOrNull { it.property == "Line_Break" }
            ?: return emptyList()
        if (st.entries.isEmpty()) return emptyList()
        return PuaaTextUtility.createRunsFromEntries(st.entries).map { e ->
            "${PuaaTextUtility.joinRange(e.firstCodePoint, e.lastCodePoint)};${e.value ?: ""}"
        }
    }
}
