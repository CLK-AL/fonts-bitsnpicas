package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `SpecialCasing.txt`.
 *
 * Format: `XXXX; lower; title; upper; [condition;]`
 *
 * Ported from the frozen Java `SpecialCasingCodec`.
 */
public class SpecialCasingCodec : PuaaCodec {

    override val fileName: String = "SpecialCasing.txt"
    override val propertyNames: List<String> = listOf(
        "Lowercase_Mapping", "Titlecase_Mapping", "Uppercase_Mapping",
    )

    override fun compile(table: MutablePuaaTable, lines: Sequence<String>) {
        val lower = table.getOrCreateSubtable("Lowercase_Mapping")
        val title = table.getOrCreateSubtable("Titlecase_Mapping")
        val upper = table.getOrCreateSubtable("Uppercase_Mapping")
        for (line in lines) {
            val fields = PuaaTextUtility.splitLine(line) ?: continue
            if (fields.size < 4) continue
            createEntry(fields, 1)?.let { lower.add(it) }
            createEntry(fields, 2)?.let { title.add(it) }
            createEntry(fields, 3)?.let { upper.add(it) }
        }
    }

    override fun decompile(table: PuaaTable): List<String> {
        val lower = table.subtables.firstOrNull { it.property == "Lowercase_Mapping" }
        val title = table.subtables.firstOrNull { it.property == "Titlecase_Mapping" }
        val upper = table.subtables.firstOrNull { it.property == "Uppercase_Mapping" }

        val lines = linkedMapOf<String, Array<String?>>()
        if (lower != null) addLines(lines, lower, 1)
        if (title != null) addLines(lines, title, 2)
        if (upper != null) addLines(lines, upper, 3)
        return lines.values.map { joinLine(it) }
    }

    private fun createEntry(fields: Array<String>, i: Int): PuaaEntry.CaseMapping? {
        return try {
            val r = PuaaTextUtility.splitRange(fields[0])
            val words = fields[i].trim().split(Regex("\\s+"))
            val values = IntArray(words.size) { words[it].toInt(16) }
            val condition = if (fields.size > 4) {
                val c = fields[4].trim()
                c.ifEmpty { null }
            } else null
            PuaaEntry.CaseMapping(r[0], r[1], values, condition)
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun addLines(
        lines: LinkedHashMap<String, Array<String?>>,
        st: PuaaSubtable,
        fieldIdx: Int,
    ) {
        for (e in st.entries) {
            for (cp in e.firstCodePoint..e.lastCodePoint) {
                var value = e.getPropertyValue(cp) ?: continue
                var condition: String? = null
                val semiIdx = value.indexOf(";")
                if (semiIdx >= 0) {
                    condition = value.substring(semiIdx + 1).trim()
                    value = value.substring(0, semiIdx).trim()
                }
                val key = (0xC0000000.toInt() + cp).toString(16) + (condition ?: "")
                val line = lines.getOrPut(key) {
                    val arr = arrayOfNulls<String>(5)
                    arr[0] = PuaaTextUtility.toHexString(cp)
                    arr[4] = condition
                    arr
                }
                line[fieldIdx] = value
            }
        }
    }

    private fun joinLine(line: Array<String?>): String {
        val sb = StringBuilder()
        sb.append(line[0] ?: "")
        sb.append("; ")
        sb.append(line[1] ?: "")
        sb.append("; ")
        sb.append(line[2] ?: "")
        sb.append("; ")
        sb.append(line[3] ?: "")
        sb.append(";")
        val cond = line[4]
        if (!cond.isNullOrEmpty()) {
            sb.append(" ")
            sb.append(cond)
            sb.append(";")
        }
        return sb.toString()
    }
}
