package com.kreative.bitsnpicas.core.puaa

/**
 * Base class for Unihan property codecs.
 *
 * Format (tab-separated): `U+XXXX\tkPropName\tvalue`
 *
 * Ported from the frozen Java `AbstractUnihanCodec`.
 */
public open class UnihanCodec(
    override val fileName: String,
    override val propertyNames: List<String>,
) : PuaaCodec {

    override fun compile(table: MutablePuaaTable, lines: Sequence<String>) {
        val props = mutableMapOf<String, MutableMap<Int, String>>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val fields = trimmed.split(Regex("\\s+"), limit = 3)
            if (fields.size < 3) continue
            try {
                val cpStr = fields[0].replace(Regex("^([Uu][+]|0[Xx])"), "")
                val cp = cpStr.toInt(16)
                val prop = fields[1]
                val value = fields[2]
                props.getOrPut(prop) { mutableMapOf() }[cp] = value
            } catch (_: NumberFormatException) {
                // skip malformed lines
            }
        }

        for ((prop, map) in props) {
            val st = table.getOrCreateSubtable(prop)
            val decEntries = toDecimalEntries(map)
            if (decEntries != null) {
                st.addAll(decEntries)
            } else {
                val hexEntries = toHexadecimalEntries(map)
                if (hexEntries != null) {
                    st.addAll(hexEntries)
                } else {
                    st.addAll(PuaaTextUtility.createEntriesFromNameMap(map))
                }
            }
        }
    }

    override fun decompile(table: PuaaTable): List<String> {
        // Collect per-codepoint per-property values into a sorted map.
        val props = sortedMapOf<Int, MutableMap<String, String>>()
        for (prop in propertyNames) {
            val st = table.subtables.firstOrNull { it.property == prop } ?: continue
            if (st.entries.isEmpty()) continue
            for ((cp, value) in PuaaTextUtility.createMapFromEntries(st.entries)) {
                if (value.isEmpty()) continue
                props.getOrPut(cp) { sortedMapOf() }[prop] = value
            }
        }

        val result = mutableListOf<String>()
        for ((cp, propMap) in props) {
            val cpStr = "U+" + PuaaTextUtility.toHexString(cp)
            for (prop in propertyNames) {
                val value = propMap[prop]
                if (value.isNullOrEmpty()) continue
                result.add("$cpStr\t$prop\t$value")
            }
        }
        return result
    }

    private fun toDecimalEntries(smap: Map<Int, String>): List<PuaaEntry>? {
        val dmap = mutableMapOf<Int, Int>()
        for ((cp, sv) in smap) {
            val iv = sv.toIntOrNull(10) ?: return null
            if (iv.toString() != sv) return null
            dmap[cp] = iv
        }
        return PuaaTextUtility.createEntriesFromDecimalMap(dmap)
    }

    private fun toHexadecimalEntries(smap: Map<Int, String>): List<PuaaEntry>? {
        val dmap = mutableMapOf<Int, Int>()
        for ((cp, sv) in smap) {
            val iv = sv.toIntOrNull(16) ?: return null
            if (PuaaTextUtility.toHexString(iv) != sv) return null
            dmap[cp] = iv
        }
        return PuaaTextUtility.createEntriesFromHexadecimalMap(dmap)
    }
}
