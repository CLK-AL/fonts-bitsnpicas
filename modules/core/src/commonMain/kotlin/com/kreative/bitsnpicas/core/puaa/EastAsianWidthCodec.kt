package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `EastAsianWidth.txt`.
 *
 * Format: `XXXX..YYYY;EAW_Value`
 *
 * Ported from the frozen Java `AbstractStringCodec` / `EastAsianWidthCodec`.
 */
public class EastAsianWidthCodec : PuaaCodec {

    override val fileName: String = "EastAsianWidth.txt"
    override val propertyNames: List<String> = listOf("East_Asian_Width")

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
        val st = table.getOrCreateSubtable("East_Asian_Width")
        st.addAll(PuaaTextUtility.createEntriesFromStringMap(values))
    }

    override fun decompile(table: PuaaTable): List<String> {
        val st = table.subtables.firstOrNull { it.property == "East_Asian_Width" }
            ?: return emptyList()
        if (st.entries.isEmpty()) return emptyList()
        return PuaaTextUtility.createRunsFromEntries(st.entries).map { e ->
            "${PuaaTextUtility.joinRange(e.firstCodePoint, e.lastCodePoint)};${e.value ?: ""}"
        }
    }
}
