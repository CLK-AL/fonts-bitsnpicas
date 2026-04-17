package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `UnicodeData.txt`.
 *
 * This is a simplified port that handles the core fields:
 * code-point, Name, General_Category, Canonical_Combining_Class,
 * Bidi_Class, Bidi_Mirrored, Simple_Uppercase/Lowercase/Titlecase_Mapping.
 *
 * Ported from the frozen Java `com.kreative.bitsnpicas.puaa.UnicodeDataCodec`.
 */
public class UnicodeDataCodec : PuaaCodec {

    override val fileName: String = "UnicodeData.txt"

    override val propertyNames: List<String> = listOf(
        "Name", "General_Category", "Canonical_Combining_Class",
        "Bidi_Class", "Bidi_Mirrored",
        "Simple_Uppercase_Mapping", "Simple_Lowercase_Mapping",
        "Simple_Titlecase_Mapping",
    )

    override fun compile(table: MutablePuaaTable, lines: Sequence<String>) {
        val names = mutableMapOf<Int, String>()
        val categories = mutableMapOf<Int, String>()
        val combClasses = mutableMapOf<Int, String>()
        val bidiClasses = mutableMapOf<Int, String>()
        val bidiMirrored = mutableMapOf<Int, Boolean>()
        val uppercase = mutableMapOf<Int, String>()
        val lowercase = mutableMapOf<Int, String>()
        val titlecase = mutableMapOf<Int, String>()

        for (line in lines) {
            val fields = PuaaTextUtility.splitLine(line) ?: continue
            val cp = parseHex(fields, 0) ?: continue
            parseString(fields, 1)?.let { names[cp] = it }
            parseString(fields, 2)?.let { categories[cp] = it }
            parseString(fields, 3)?.let { combClasses[cp] = it }
            parseString(fields, 4)?.let { bidiClasses[cp] = it }
            parseBoolean(fields, 9)?.let { bidiMirrored[cp] = it }
            parseString(fields, 12)?.let { uppercase[cp] = it }
            parseString(fields, 13)?.let { lowercase[cp] = it }
            parseString(fields, 14)?.let { titlecase[cp] = it }
        }

        addStringSubtable(table, "Name", names)
        addStringSubtable(table, "General_Category", categories)
        addStringSubtable(table, "Canonical_Combining_Class", combClasses)
        addStringSubtable(table, "Bidi_Class", bidiClasses)

        val bidiSt = table.getOrCreateSubtable("Bidi_Mirrored")
        bidiSt.addAll(PuaaTextUtility.createEntriesFromBooleanMap(bidiMirrored))

        addStringSubtable(table, "Simple_Uppercase_Mapping", uppercase)
        addStringSubtable(table, "Simple_Lowercase_Mapping", lowercase)
        addStringSubtable(table, "Simple_Titlecase_Mapping", titlecase)
    }

    override fun decompile(table: PuaaTable): List<String> {
        // Collect per-codepoint field values.
        val lines = sortedMapOf<Int, Array<String?>>()

        fun addEntries(propName: String, fieldIndex: Int) {
            val st = table.subtables.firstOrNull { it.property == propName } ?: return
            for (e in st.entries) {
                for (cp in e.firstCodePoint..e.lastCodePoint) {
                    val value = e.getPropertyValue(cp)
                    if (value.isNullOrEmpty()) continue
                    val line = lines.getOrPut(cp) { arrayOfNulls(15) }
                    if (line[0] == null) line[0] = PuaaTextUtility.toHexString(cp)
                    if (line[fieldIndex] == null) line[fieldIndex] = value
                }
            }
        }

        addEntries("Name", 1)
        addEntries("General_Category", 2)
        addEntries("Canonical_Combining_Class", 3)
        addEntries("Bidi_Class", 4)
        addEntries("Bidi_Mirrored", 9)
        addEntries("Simple_Uppercase_Mapping", 12)
        addEntries("Simple_Lowercase_Mapping", 13)
        addEntries("Simple_Titlecase_Mapping", 14)

        return lines.values.map { fields ->
            PuaaTextUtility.joinLine(fields, ";")
        }
    }

    private fun addStringSubtable(
        table: MutablePuaaTable,
        property: String,
        map: Map<Int, String>,
    ) {
        if (map.isEmpty()) return
        val st = table.getOrCreateSubtable(property)
        st.addAll(PuaaTextUtility.createEntriesFromStringMap(map))
    }

    private fun parseHex(fields: Array<String>, i: Int): Int? {
        if (i >= fields.size) return null
        return fields[i].trim().toIntOrNull(16)
    }

    private fun parseString(fields: Array<String>, i: Int): String? {
        if (i >= fields.size) return null
        val s = fields[i].trim()
        return s.ifEmpty { null }
    }

    private fun parseBoolean(fields: Array<String>, i: Int): Boolean? {
        if (i >= fields.size) return null
        val s = fields[i].trim()
        if (s.isEmpty()) return null
        return s.equals("Y", ignoreCase = true)
    }
}
