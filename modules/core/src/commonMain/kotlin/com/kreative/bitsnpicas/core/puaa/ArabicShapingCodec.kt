package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `ArabicShaping.txt`.
 *
 * Format: `XXXX; Name; Joining_Type; Joining_Group`
 *
 * Ported from the frozen Java `ArabicShapingCodec`.
 */
public class ArabicShapingCodec : PuaaCodec {

    override val fileName: String = "ArabicShaping.txt"
    override val propertyNames: List<String> = listOf("Joining_Type", "Joining_Group")

    override fun compile(table: MutablePuaaTable, lines: Sequence<String>) {
        val types = mutableMapOf<Int, String>()
        val groups = mutableMapOf<Int, String>()
        for (line in lines) {
            val fields = PuaaTextUtility.splitLine(line) ?: continue
            if (fields.size < 4) continue
            try {
                val r = PuaaTextUtility.splitRange(fields[0])
                val t = fields[2].trim()
                val g = fields[3].trim()
                for (cp in r[0]..r[1]) {
                    types[cp] = t
                    groups[cp] = g
                }
            } catch (_: NumberFormatException) {
                // skip malformed lines
            }
        }
        val stType = table.getOrCreateSubtable("Joining_Type")
        stType.addAll(PuaaTextUtility.createEntriesFromStringMap(types))
        val stGroup = table.getOrCreateSubtable("Joining_Group")
        stGroup.addAll(PuaaTextUtility.createEntriesFromNameMap(groups))
    }

    override fun decompile(table: PuaaTable): List<String> {
        val names = table.subtables.firstOrNull { it.property == "Name" }
        val types = table.subtables.firstOrNull { it.property == "Joining_Type" }
        val groups = table.subtables.firstOrNull { it.property == "Joining_Group" }

        val lines = sortedMapOf<Int, Array<String?>>()
        if (types != null) addEntries(names, lines, types, 2)
        if (groups != null) addEntries(names, lines, groups, 3)
        return lines.values.map { PuaaTextUtility.joinLine(it, "; ") }
    }

    private fun addEntries(
        names: PuaaSubtable?,
        lines: MutableMap<Int, Array<String?>>,
        st: PuaaSubtable,
        fieldIdx: Int,
    ) {
        for (e in st.entries) {
            for (cp in e.firstCodePoint..e.lastCodePoint) {
                val value = e.getPropertyValue(cp)
                if (value.isNullOrEmpty()) continue
                val line = lines.getOrPut(cp) {
                    val arr = arrayOfNulls<String>(4)
                    arr[0] = PuaaTextUtility.toHexString(cp)
                    if (names != null) arr[1] = names.getPropertyValue(cp)
                    arr
                }
                line[fieldIdx] = (line[fieldIdx] ?: "") + value
            }
        }
    }
}
