package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `VerticalOrientation.txt`.
 *
 * Format: `XXXX..YYYY ; VO_Value`
 *
 * Ported from the frozen Java `AbstractStringCodec` / `VerticalOrientationCodec`.
 */
public class VerticalOrientationCodec : PuaaCodec {

    override val fileName: String = "VerticalOrientation.txt"
    override val propertyNames: List<String> = listOf("Vertical_Orientation")

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
        val st = table.getOrCreateSubtable("Vertical_Orientation")
        st.addAll(PuaaTextUtility.createEntriesFromStringMap(values))
    }

    override fun decompile(table: PuaaTable): List<String> {
        val st = table.subtables.firstOrNull { it.property == "Vertical_Orientation" }
            ?: return emptyList()
        if (st.entries.isEmpty()) return emptyList()
        return PuaaTextUtility.createRunsFromEntries(st.entries).map { e ->
            val sb = StringBuilder()
            sb.append(PuaaTextUtility.joinRange(e.firstCodePoint, e.lastCodePoint))
            while (sb.length < 14) sb.append(' ')
            sb.append("; ")
            sb.append(e.value ?: "")
            sb.toString()
        }
    }
}
