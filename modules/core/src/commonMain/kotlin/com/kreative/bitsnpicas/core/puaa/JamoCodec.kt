package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `Jamo.txt`.
 *
 * Format: `XXXX; ShortName` (value may be empty for U+110B).
 *
 * Ported from the frozen Java `JamoCodec`.
 */
public class JamoCodec : PuaaCodec {

    override val fileName: String = "Jamo.txt"
    override val propertyNames: List<String> = listOf("Jamo_Short_Name")

    override fun compile(table: MutablePuaaTable, lines: Sequence<String>) {
        val jamo = table.getOrCreateSubtable("Jamo_Short_Name")
        for (line in lines) {
            val fields = PuaaTextUtility.splitLine(line) ?: continue
            if (fields.isEmpty()) continue
            try {
                val r = PuaaTextUtility.splitRange(fields[0])
                val v = if (fields.size > 1) fields[1].trim() else ""
                jamo.add(PuaaEntry.Single(r[0], r[1], v))
            } catch (_: NumberFormatException) {
                // skip malformed lines
            }
        }
    }

    override fun decompile(table: PuaaTable): List<String> {
        val jamo = table.subtables.firstOrNull { it.property == "Jamo_Short_Name" }
            ?: return emptyList()
        if (jamo.entries.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        for (e in jamo.entries) {
            for (cp in e.firstCodePoint..e.lastCodePoint) {
                result.add("${PuaaTextUtility.toHexString(cp)}; ${e.getPropertyValue(cp) ?: ""}")
            }
        }
        return result
    }
}
