package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `NameAliases.txt`.
 *
 * Format: `XXXX;Alias;Type`
 *
 * Ported from the frozen Java `NameAliasesCodec`.
 */
public class NameAliasesCodec : PuaaCodec {

    override val fileName: String = "NameAliases.txt"
    override val propertyNames: List<String> = listOf("Name_Alias")

    override fun compile(table: MutablePuaaTable, lines: Sequence<String>) {
        val names = table.getOrCreateSubtable("Name_Alias")
        for (line in lines) {
            val fields = PuaaTextUtility.splitLine(line) ?: continue
            if (fields.size < 3) continue
            try {
                val r = PuaaTextUtility.splitRange(fields[0])
                val n = fields[1].trim()
                val t = fields[2].trim()
                names.add(PuaaEntry.NameAlias(r[0], r[1], n, t))
            } catch (_: NumberFormatException) {
                // skip malformed lines
            }
        }
    }

    override fun decompile(table: PuaaTable): List<String> {
        val names = table.subtables.firstOrNull { it.property == "Name_Alias" }
            ?: return emptyList()
        if (names.entries.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        for (e in names.entries) {
            for (cp in e.firstCodePoint..e.lastCodePoint) {
                val value = e.getPropertyValue(cp) ?: continue
                result.add("${PuaaTextUtility.toHexString(cp)};$value")
            }
        }
        return result
    }
}
