package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `PropList.txt` and similar boolean-property-list files.
 *
 * Format: `XXXX..YYYY ; Property_Name`
 *
 * Ported from the frozen Java `AbstractPropListCodec` / `PropListCodec`.
 */
public open class PropListCodec(
    override val fileName: String = "PropList.txt",
    override val propertyNames: List<String> = DEFAULT_PROP_NAMES,
) : PuaaCodec {

    override fun compile(table: MutablePuaaTable, lines: Sequence<String>) {
        // Accumulate boolean maps per property.
        val props = mutableMapOf<String, MutableMap<Int, Boolean>>()
        for (line in lines) {
            val fields = PuaaTextUtility.splitLine(line) ?: continue
            if (fields.size < 2) continue
            try {
                val r = PuaaTextUtility.splitRange(fields[0])
                val prop = fields[1].trim()
                val p = props.getOrPut(prop) { mutableMapOf() }
                for (cp in r[0]..r[1]) p[cp] = true
            } catch (_: NumberFormatException) {
                // skip malformed lines
            }
        }

        for ((prop, map) in props) {
            val st = table.getOrCreateSubtable(prop)
            st.addAll(PuaaTextUtility.createEntriesFromBooleanMap(map))
        }
    }

    override fun decompile(table: PuaaTable): List<String> {
        val result = mutableListOf<String>()
        for (prop in propertyNames) {
            val st = table.subtables.firstOrNull { it.property == prop }
                ?: continue
            if (st.entries.isEmpty()) continue

            for (e in PuaaTextUtility.createRunsFromEntries(st.entries)) {
                if (e.value?.equals("Y", ignoreCase = true) != true) continue
                val sb = StringBuilder()
                sb.append(PuaaTextUtility.joinRange(e.firstCodePoint, e.lastCodePoint))
                while (sb.length < 14) sb.append(' ')
                sb.append("; ")
                sb.append(prop)
                result.add(sb.toString())
            }
        }
        return result
    }

    public companion object {
        public val DEFAULT_PROP_NAMES: List<String> = listOf(
            "White_Space", "Bidi_Control", "Join_Control", "Dash",
            "Hyphen", "Quotation_Mark", "Terminal_Punctuation",
            "Other_Math", "Hex_Digit", "ASCII_Hex_Digit",
            "Other_Alphabetic", "Ideographic", "Diacritic", "Extender",
            "Other_Lowercase", "Other_Uppercase",
            "Noncharacter_Code_Point", "Other_Grapheme_Extend",
            "IDS_Binary_Operator", "IDS_Trinary_Operator",
            "IDS_Unary_Operator", "Radical", "Unified_Ideograph",
            "Other_Default_Ignorable_Code_Point", "Deprecated",
            "Soft_Dotted", "Logical_Order_Exception",
            "Other_ID_Start", "Other_ID_Continue",
            "ID_Compat_Math_Continue", "ID_Compat_Math_Start",
            "Sentence_Terminal", "Variation_Selector",
            "Pattern_White_Space", "Pattern_Syntax",
            "Prepended_Concatenation_Mark", "Regional_Indicator",
        )
    }
}
