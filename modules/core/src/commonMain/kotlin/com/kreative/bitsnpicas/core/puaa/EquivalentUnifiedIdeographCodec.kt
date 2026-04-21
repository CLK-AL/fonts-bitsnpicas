package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `EquivalentUnifiedIdeograph.txt`.
 *
 * Format: `XXXX..YYYY ; ZZZZ`
 *
 * Ported from the frozen Java `EquivalentUnifiedIdeographCodec`.
 */
public class EquivalentUnifiedIdeographCodec : PuaaCodec {

    override val fileName: String = "EquivalentUnifiedIdeograph.txt"
    override val propertyNames: List<String> = listOf("Equivalent_Unified_Ideograph")

    override fun compile(table: MutablePuaaTable, lines: Sequence<String>) {
        val values = mutableMapOf<Int, Int>()
        for (line in lines) {
            val fields = PuaaTextUtility.splitLine(line) ?: continue
            if (fields.size < 2) continue
            try {
                val r = PuaaTextUtility.splitRange(fields[0])
                val v = fields[1].trim().toInt(16)
                for (cp in r[0]..r[1]) values[cp] = v
            } catch (_: NumberFormatException) {
                // skip malformed lines
            }
        }
        val st = table.getOrCreateSubtable("Equivalent_Unified_Ideograph")
        st.addAll(PuaaTextUtility.createEntriesFromHexadecimalMap(values))
    }

    override fun decompile(table: PuaaTable): List<String> {
        val st = table.subtables.firstOrNull { it.property == "Equivalent_Unified_Ideograph" }
            ?: return emptyList()
        if (st.entries.isEmpty()) return emptyList()
        return PuaaTextUtility.createRunsFromEntries(st.entries).map { e ->
            val sb = StringBuilder()
            sb.append(PuaaTextUtility.joinRange(e.firstCodePoint, e.lastCodePoint))
            while (sb.length < 11) sb.append(' ')
            sb.append("; ")
            sb.append(e.value ?: "")
            sb.toString()
        }
    }
}
