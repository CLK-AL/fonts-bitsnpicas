package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `BidiMirroring.txt`.
 *
 * Format: `XXXX; YYYY`
 *
 * Ported from the frozen Java `BidiMirroringCodec`.
 */
public class BidiMirroringCodec : PuaaCodec {

    override val fileName: String = "BidiMirroring.txt"
    override val propertyNames: List<String> = listOf("Bidi_Mirroring_Glyph")

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
        val st = table.getOrCreateSubtable("Bidi_Mirroring_Glyph")
        st.addAll(PuaaTextUtility.createEntriesFromHexadecimalMap(values))
    }

    override fun decompile(table: PuaaTable): List<String> {
        val st = table.subtables.firstOrNull { it.property == "Bidi_Mirroring_Glyph" }
            ?: return emptyList()
        if (st.entries.isEmpty()) return emptyList()
        return PuaaTextUtility.createMapFromEntries(st.entries).map { (cp, value) ->
            "${PuaaTextUtility.toHexString(cp)}; $value"
        }
    }
}
