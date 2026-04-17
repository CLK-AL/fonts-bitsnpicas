package com.kreative.bitsnpicas.core.puaa

import com.kreative.bitsnpicas.core.ByteReader
import com.kreative.bitsnpicas.core.ByteWriter
import com.kreative.bitsnpicas.core.ParseException

/**
 * The PUAA binary table container. Holds a list of [PuaaSubtable]s,
 * each of which contains [PuaaEntry] instances.
 *
 * The binary layout matches the frozen Java `PuaaTable` exactly:
 *
 * ```
 * Table header:
 *   u16 version
 *   u16 propertyCount
 *   For each property:
 *     u32 propertyNameOffset
 *     u32 subtableHeaderOffset
 *
 * Subtable headers (one per property):
 *   u16 entryCount
 *   For each entry:
 *     u8  entryType
 *     u8  plane (bits 16..23 of code point)
 *     u16 firstCodePoint (low 16 bits)
 *     u16 lastCodePoint  (low 16 bits)
 *     u32 entryData
 *
 * Entry data blocks (variable-length records for Multi/HexMulti/HexSeq/CaseMapping/NameAlias):
 *   u16 valueCount
 *   u32[] values
 *
 * String table:
 *   length-prefixed UTF-8 strings (u8 length, then raw bytes)
 * ```
 *
 * Short strings (≤4 ASCII bytes, all <0x80) can be "minified" into
 * the 32-bit entryData field directly (high bit set as marker).
 */
public data class PuaaTable(
    val version: Int = DEFAULT_VERSION,
    val subtables: List<PuaaSubtable>,
) {
    public companion object {
        public const val DEFAULT_VERSION: Int = 1

        /**
         * Decompile a PUAA table from its binary representation.
         */
        public fun decompile(data: ByteArray): PuaaTable {
            val r = ByteReader(data)

            // Read table header.
            val version = r.readU16BE()
            val propertyCount = r.readU16BE()
            val propertyNameOffset = IntArray(propertyCount)
            val subtableHeaderOffset = IntArray(propertyCount)
            for (i in 0 until propertyCount) {
                propertyNameOffset[i] = r.readIntBE()
                subtableHeaderOffset[i] = r.readIntBE()
            }

            // Read subtables.
            val subtables = mutableListOf<PuaaSubtable>()
            for (i in 0 until propertyCount) {
                // Read property name.
                val property = readString(data, propertyNameOffset[i])
                    ?: throw ParseException("null property name for subtable $i")

                // Read subtable header.
                r.seek(subtableHeaderOffset[i])
                val entryCount = r.readU16BE()
                val entryType = IntArray(entryCount)
                val firstCodePoint = IntArray(entryCount)
                val lastCodePoint = IntArray(entryCount)
                val entryData = IntArray(entryCount)
                for (j in 0 until entryCount) {
                    entryType[j] = r.readU8()
                    val plane = r.readU8() shl 16
                    firstCodePoint[j] = r.readU16BE() or plane
                    lastCodePoint[j] = r.readU16BE() or plane
                    entryData[j] = r.readIntBE()
                }

                // Read entries.
                val entries = mutableListOf<PuaaEntry>()
                for (j in 0 until entryCount) {
                    val fcp = firstCodePoint[j]
                    val lcp = lastCodePoint[j]
                    when (entryType[j]) {
                        PuaaEntry.TYPE_SINGLE -> {
                            entries.add(PuaaEntry.Single(fcp, lcp, readString(data, entryData[j])))
                        }

                        PuaaEntry.TYPE_MULTIPLE -> {
                            val mv = readIntArray(data, entryData[j])
                            val values = if (mv != null) {
                                mv.map { readString(data, it) }
                            } else {
                                emptyList()
                            }
                            entries.add(PuaaEntry.Multiple(fcp, lcp, values))
                        }

                        PuaaEntry.TYPE_BOOLEAN -> {
                            entries.add(PuaaEntry.BooleanEntry(fcp, lcp, entryData[j] != 0))
                        }

                        PuaaEntry.TYPE_DECIMAL -> {
                            entries.add(PuaaEntry.Decimal(fcp, lcp, entryData[j]))
                        }

                        PuaaEntry.TYPE_HEXADECIMAL -> {
                            entries.add(PuaaEntry.Hexadecimal(fcp, lcp, entryData[j]))
                        }

                        PuaaEntry.TYPE_HEXMULTIPLE -> {
                            val values = readIntArray(data, entryData[j]) ?: intArrayOf()
                            entries.add(PuaaEntry.HexMultiple(fcp, lcp, values))
                        }

                        PuaaEntry.TYPE_HEXSEQUENCE -> {
                            val values = readIntArray(data, entryData[j]) ?: intArrayOf()
                            entries.add(PuaaEntry.HexSequence(fcp, lcp, values))
                        }

                        PuaaEntry.TYPE_CASEMAPPING -> {
                            val cv = readIntArray(data, entryData[j])
                            if (cv != null && cv.isNotEmpty()) {
                                val n = cv.size - 1
                                val values = IntArray(n) { cv[it] }
                                val condition = readString(data, cv[n])
                                entries.add(PuaaEntry.CaseMapping(fcp, lcp, values, condition))
                            } else {
                                entries.add(PuaaEntry.CaseMapping(fcp, lcp, intArrayOf(), null))
                            }
                        }

                        PuaaEntry.TYPE_NAMEALIAS -> {
                            val nv = readIntArray(data, entryData[j])
                            if (nv != null && nv.size > 1) {
                                val alias = readString(data, nv[0])
                                val type = readString(data, nv[1])
                                entries.add(PuaaEntry.NameAlias(fcp, lcp, alias, type))
                            } else {
                                entries.add(PuaaEntry.NameAlias(fcp, lcp, null, null))
                            }
                        }

                        else -> throw ParseException("Invalid PUAA entry type: ${entryType[j]}")
                    }
                }

                subtables.add(PuaaSubtable(property, entries))
            }

            return PuaaTable(version, subtables)
        }

        // ---- decompile helpers ------------------------------------------------

        private fun readString(data: ByteArray, offset: Int): String? {
            if (offset > 0) {
                val len = data[offset].toInt() and 0xFF
                return data.decodeToString(offset + 1, offset + 1 + len)
            }
            if (offset < 0) {
                val sb = StringBuilder()
                val ch0 = ((offset shr 24) and 0x7F).toChar()
                if (ch0.code != 0) sb.append(ch0)
                val ch1 = ((offset shr 16) and 0x7F).toChar()
                if (ch1.code != 0) sb.append(ch1)
                val ch2 = ((offset shr 8) and 0x7F).toChar()
                if (ch2.code != 0) sb.append(ch2)
                val ch3 = ((offset shr 0) and 0x7F).toChar()
                if (ch3.code != 0) sb.append(ch3)
                return sb.toString()
            }
            return null // offset == 0 => null
        }

        private fun readIntArray(data: ByteArray, offset: Int): IntArray? {
            if (offset <= 0) return null
            val r = ByteReader(data)
            r.seek(offset)
            val count = r.readU16BE()
            return IntArray(count) { r.readIntBE() }
        }
    }

    /**
     * Compile this table into its binary representation.
     * The output is byte-exact with the frozen Java `PuaaTable.compile()`.
     */
    public fun compile(): ByteArray {
        // Sort subtables by property name, and entries within each by code point.
        // Filter out empty subtables.
        val sorted = subtables
            .filter { it.entries.isNotEmpty() }
            .map { st ->
                PuaaSubtable(
                    st.property,
                    st.entries.sortedWith(compareBy({ it.firstCodePoint }, { it.lastCodePoint })),
                )
            }
            .sortedBy { it.property }

        val propertyCount = sorted.size

        // Pre-allocated offset arrays.
        val propertyNameOffset = IntArray(propertyCount)
        val subtableHeaderOffset = IntArray(propertyCount)
        val entryCount = IntArray(propertyCount) { sorted[it].entries.size }
        val entryType = Array(propertyCount) { IntArray(entryCount[it]) }
        val entryData = Array(propertyCount) { IntArray(entryCount[it]) }
        val valueCount = Array(propertyCount) { IntArray(entryCount[it]) }
        val valueData = Array(propertyCount) { arrayOfNulls<IntArray>(entryCount[it]) }

        // String interning table.
        val stringTable = mutableMapOf<String, Int>()
        val stringData = mutableListOf<ByteArray>()

        // p = running byte offset.
        var p = 4 + propertyCount * 8

        // Calculate subtable header offsets.
        for (i in 0 until propertyCount) {
            subtableHeaderOffset[i] = p
            p += 2 + entryCount[i] * 10
        }

        // Calculate entry header values.
        for (i in 0 until propertyCount) {
            for (j in 0 until entryCount[i]) {
                val e = sorted[i].entries[j]
                when (e) {
                    is PuaaEntry.Single -> {
                        entryType[i][j] = PuaaEntry.TYPE_SINGLE
                        // entryData set later during string pass
                    }
                    is PuaaEntry.Multiple -> {
                        entryType[i][j] = PuaaEntry.TYPE_MULTIPLE
                        entryData[i][j] = p
                        valueCount[i][j] = e.values.size
                        valueData[i][j] = IntArray(e.values.size)
                        p += 2 + e.values.size * 4
                    }
                    is PuaaEntry.BooleanEntry -> {
                        entryType[i][j] = PuaaEntry.TYPE_BOOLEAN
                        entryData[i][j] = if (e.value) -1 else 0
                    }
                    is PuaaEntry.Decimal -> {
                        entryType[i][j] = PuaaEntry.TYPE_DECIMAL
                        entryData[i][j] = e.value
                    }
                    is PuaaEntry.Hexadecimal -> {
                        entryType[i][j] = PuaaEntry.TYPE_HEXADECIMAL
                        entryData[i][j] = e.value
                    }
                    is PuaaEntry.HexMultiple -> {
                        entryType[i][j] = PuaaEntry.TYPE_HEXMULTIPLE
                        entryData[i][j] = p
                        valueCount[i][j] = e.values.size
                        valueData[i][j] = e.values.copyOf()
                        p += 2 + e.values.size * 4
                    }
                    is PuaaEntry.HexSequence -> {
                        entryType[i][j] = PuaaEntry.TYPE_HEXSEQUENCE
                        entryData[i][j] = p
                        valueCount[i][j] = e.values.size
                        valueData[i][j] = e.values.copyOf()
                        p += 2 + e.values.size * 4
                    }
                    is PuaaEntry.CaseMapping -> {
                        entryType[i][j] = PuaaEntry.TYPE_CASEMAPPING
                        entryData[i][j] = p
                        valueCount[i][j] = e.values.size + 1
                        valueData[i][j] = IntArray(e.values.size + 1)
                        p += 2 + (e.values.size + 1) * 4
                    }
                    is PuaaEntry.NameAlias -> {
                        entryType[i][j] = PuaaEntry.TYPE_NAMEALIAS
                        entryData[i][j] = p
                        valueCount[i][j] = 2
                        valueData[i][j] = IntArray(2)
                        p += 10
                    }
                }
            }
        }

        // Calculate property name offsets (always forceFull=true).
        for (i in 0 until propertyCount) {
            p = setString(propertyNameOffset, i, sorted[i].property, stringTable, stringData, p, true)
        }

        // Calculate string data offsets for entry values.
        for (i in 0 until propertyCount) {
            for (j in 0 until entryCount[i]) {
                val e = sorted[i].entries[j]
                when (e) {
                    is PuaaEntry.Single -> {
                        p = setString(entryData[i], j, e.value, stringTable, stringData, p, false)
                    }
                    is PuaaEntry.Multiple -> {
                        val vd = valueData[i][j]!!
                        for (k in e.values.indices) {
                            p = setString(vd, k, e.values[k], stringTable, stringData, p, false)
                        }
                    }
                    is PuaaEntry.CaseMapping -> {
                        val vd = valueData[i][j]!!
                        for (k in e.values.indices) {
                            vd[k] = e.values[k]
                        }
                        p = setString(vd, e.values.size, e.condition, stringTable, stringData, p, false)
                    }
                    is PuaaEntry.NameAlias -> {
                        val vd = valueData[i][j]!!
                        p = setString(vd, 0, e.alias, stringTable, stringData, p, false)
                        p = setString(vd, 1, e.type, stringTable, stringData, p, false)
                    }
                    else -> { /* no string data for other types */ }
                }
            }
        }

        // Write everything.
        val w = ByteWriter(p)

        // Table header.
        w.writeU16BE(version)
        w.writeU16BE(propertyCount)
        for (i in 0 until propertyCount) {
            w.writeIntBE(propertyNameOffset[i])
            w.writeIntBE(subtableHeaderOffset[i])
        }

        // Subtable headers.
        for (i in 0 until propertyCount) {
            w.writeU16BE(entryCount[i])
            for (j in 0 until entryCount[i]) {
                val e = sorted[i].entries[j]
                w.writeU8(entryType[i][j])
                w.writeU8(e.firstCodePoint shr 16)
                w.writeU16BE(e.firstCodePoint and 0xFFFF)
                w.writeU16BE(e.lastCodePoint and 0xFFFF)
                w.writeIntBE(entryData[i][j])
            }
        }

        // Entry data blocks.
        for (i in 0 until propertyCount) {
            for (j in 0 until entryCount[i]) {
                val vd = valueData[i][j]
                if (vd != null) {
                    w.writeU16BE(valueCount[i][j])
                    for (k in 0 until valueCount[i][j]) {
                        w.writeIntBE(vd[k])
                    }
                }
            }
        }

        // String data.
        for (d in stringData) {
            w.writeU8(d.size)
            w.writeBytes(d)
        }

        return w.toByteArray()
    }
}

// ---- compile helpers ----------------------------------------------------

/**
 * Try to pack a short ASCII string into a 32-bit int with the high bit set.
 * Returns the packed value, or 0 if the string can't be minified.
 */
private fun minify(d: ByteArray): Int {
    if (d.size > 4) return 0
    var value = Int.MIN_VALUE // 0x80000000
    if (d.size > 3) { if (d[3] < 0) return 0; value = value or ((d[3].toInt() and 0x7F) shl 0) }
    if (d.size > 2) { if (d[2] < 0) return 0; value = value or ((d[2].toInt() and 0x7F) shl 8) }
    if (d.size > 1) { if (d[1] < 0) return 0; value = value or ((d[1].toInt() and 0x7F) shl 16) }
    if (d.size > 0) { if (d[0] < 0) return 0; value = value or ((d[0].toInt() and 0x7F) shl 24) }
    return value
}

private fun setString(
    array: IntArray,
    index: Int,
    s: String?,
    stringTable: MutableMap<String, Int>,
    stringData: MutableList<ByteArray>,
    p: Int,
    forceFull: Boolean,
): Int {
    if (s == null) {
        array[index] = 0
        return p
    }
    val existing = stringTable[s]
    if (existing != null) {
        array[index] = existing
        return p
    }
    val d = s.encodeToByteArray()
    if (!forceFull) {
        val minified = minify(d)
        if (minified != 0) {
            array[index] = minified
            return p
        }
    }
    array[index] = p
    stringTable[s] = p
    stringData.add(d)
    return p + d.size + 1
}
