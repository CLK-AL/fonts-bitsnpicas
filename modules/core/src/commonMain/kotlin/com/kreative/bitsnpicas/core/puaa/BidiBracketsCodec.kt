package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `BidiBrackets.txt`.
 *
 * Format: `XXXX; YYYY; Bracket_Type`
 *
 * Ported from the frozen Java `BidiBracketsCodec`.
 */
public class BidiBracketsCodec : PuaaCodec {

    override val fileName: String = "BidiBrackets.txt"
    override val propertyNames: List<String> = listOf(
        "Bidi_Paired_Bracket", "Bidi_Paired_Bracket_Type",
    )

    override fun compile(table: MutablePuaaTable, lines: Sequence<String>) {
        val values = mutableMapOf<Int, Int>()
        val types = mutableMapOf<Int, String>()
        for (line in lines) {
            val fields = PuaaTextUtility.splitLine(line) ?: continue
            if (fields.size < 3) continue
            try {
                val r = PuaaTextUtility.splitRange(fields[0])
                val v = fields[1].trim().toInt(16)
                val t = fields[2].trim()
                for (cp in r[0]..r[1]) {
                    values[cp] = v
                    types[cp] = t
                }
            } catch (_: NumberFormatException) {
                // skip malformed lines
            }
        }
        val stValues = table.getOrCreateSubtable("Bidi_Paired_Bracket")
        stValues.addAll(PuaaTextUtility.createEntriesFromHexadecimalMap(values))
        val stTypes = table.getOrCreateSubtable("Bidi_Paired_Bracket_Type")
        stTypes.addAll(PuaaTextUtility.createEntriesFromStringMap(types))
    }

    override fun decompile(table: PuaaTable): List<String> {
        val values = table.subtables.firstOrNull { it.property == "Bidi_Paired_Bracket" }
        val types = table.subtables.firstOrNull { it.property == "Bidi_Paired_Bracket_Type" }

        val lines = sortedMapOf<Int, Array<String?>>()
        if (values != null) addEntries(lines, values, 1)
        if (types != null) addEntries(lines, types, 2)
        return lines.values.map { PuaaTextUtility.joinLine(it, "; ") }
    }

    private fun addEntries(
        lines: MutableMap<Int, Array<String?>>,
        st: PuaaSubtable,
        fieldIdx: Int,
    ) {
        for (e in st.entries) {
            for (cp in e.firstCodePoint..e.lastCodePoint) {
                val value = e.getPropertyValue(cp)
                if (value.isNullOrEmpty()) continue
                val line = lines.getOrPut(cp) {
                    val arr = arrayOfNulls<String>(3)
                    arr[0] = PuaaTextUtility.toHexString(cp)
                    arr
                }
                line[fieldIdx] = (line[fieldIdx] ?: "") + value
            }
        }
    }
}
