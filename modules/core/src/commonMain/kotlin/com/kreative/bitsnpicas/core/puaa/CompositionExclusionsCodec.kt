package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `CompositionExclusions.txt`.
 *
 * Format: one hex code point per line (single-field boolean).
 *
 * Ported from the frozen Java `CompositionExclusionsCodec`.
 */
public class CompositionExclusionsCodec : PuaaCodec {

    override val fileName: String = "CompositionExclusions.txt"
    override val propertyNames: List<String> = listOf("Composition_Exclusion")

    override fun compile(table: MutablePuaaTable, lines: Sequence<String>) {
        val values = mutableMapOf<Int, Boolean>()
        for (line in lines) {
            val fields = PuaaTextUtility.splitLine(line) ?: continue
            if (fields.isEmpty()) continue
            try {
                val r = PuaaTextUtility.splitRange(fields[0])
                for (cp in r[0]..r[1]) values[cp] = true
            } catch (_: NumberFormatException) {
                // skip malformed lines
            }
        }
        val st = table.getOrCreateSubtable("Composition_Exclusion")
        st.addAll(PuaaTextUtility.createEntriesFromBooleanMap(values))
    }

    override fun decompile(table: PuaaTable): List<String> {
        val st = table.subtables.firstOrNull { it.property == "Composition_Exclusion" }
            ?: return emptyList()
        if (st.entries.isEmpty()) return emptyList()
        return PuaaTextUtility.createMapFromEntries(st.entries)
            .filter { it.value.equals("Y", ignoreCase = true) }
            .map { (cp, _) -> PuaaTextUtility.toHexString(cp) }
    }
}
