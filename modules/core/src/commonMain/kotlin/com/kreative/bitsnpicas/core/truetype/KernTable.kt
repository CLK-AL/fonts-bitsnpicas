package com.kreative.bitsnpicas.core.truetype

import com.kreative.bitsnpicas.core.ByteReader
import com.kreative.bitsnpicas.core.ByteWriter

/**
 * The `kern` table — Kerning table.
 *
 * Contains one or more subtables with kerning pair data. Format 0
 * (ordered list of kern pairs) is fully parsed. Other subtable formats
 * are preserved as [UnknownKernSubtable] raw bytes for lossless round-trip.
 *
 * Spec: https://docs.microsoft.com/en-us/typography/opentype/spec/kern
 */
public class KernTable : TrueTypeTable() {

    override val tableName: String get() = "kern"

    public var version: Int = 0
    public val subtables: MutableList<KernSubtable> = mutableListOf()

    override fun compile(dependencies: Map<Int, TrueTypeTable>): ByteArray {
        val w = ByteWriter()
        w.writeU16BE(version)
        w.writeU16BE(subtables.size)

        for (st in subtables) {
            val stData = st.compile()
            // Subtable header: version(u16) + length(u16) + coverage(u16) = 6 bytes + data
            w.writeU16BE(st.subtableVersion)
            w.writeU16BE(stData.size + 6) // length includes the 6-byte header
            w.writeU16BE(st.coverage)
            w.writeBytes(stData)
        }

        return w.toByteArray()
    }

    override fun decompile(data: ByteArray, dependencies: Map<Int, TrueTypeTable>) {
        val r = ByteReader(data)
        version = r.readU16BE()
        val numSubtables = r.readU16BE()

        subtables.clear()
        for (i in 0 until numSubtables) {
            val stVersion = r.readU16BE()
            val stLength = r.readU16BE()
            val stCoverage = r.readU16BE()
            val stDataLength = stLength - 6 // subtract the 6-byte header
            val format = (stCoverage shr 8) and 0xFF

            if (format == 0 && stDataLength > 0) {
                val st = KernFormat0Subtable()
                st.subtableVersion = stVersion
                st.coverage = stCoverage
                st.decompile(r)
                subtables.add(st)
            } else {
                val raw = if (stDataLength > 0) r.readBytes(stDataLength) else ByteArray(0)
                val st = UnknownKernSubtable(raw)
                st.subtableVersion = stVersion
                st.coverage = stCoverage
                subtables.add(st)
            }
        }
    }
}

/**
 * Base class for kern subtables.
 */
public abstract class KernSubtable {
    public var subtableVersion: Int = 0
    public var coverage: Int = 0

    /** The format number extracted from the coverage field. */
    public val format: Int get() = (coverage shr 8) and 0xFF

    /** Compile subtable data (excluding the 6-byte header). */
    public abstract fun compile(): ByteArray
}

/**
 * Kern subtable format 0 — ordered list of kern pairs.
 *
 * Each pair is (left glyph ID, right glyph ID, kern value).
 */
public class KernFormat0Subtable : KernSubtable() {

    public val pairs: MutableList<KernPair> = mutableListOf()

    public fun decompile(r: ByteReader) {
        val nPairs = r.readU16BE()
        /* searchRange */ r.readU16BE()
        /* entrySelector */ r.readU16BE()
        /* rangeShift */ r.readU16BE()

        pairs.clear()
        for (i in 0 until nPairs) {
            val left = r.readU16BE()
            val right = r.readU16BE()
            val value = r.readI16BE()
            pairs.add(KernPair(left, right, value))
        }
    }

    override fun compile(): ByteArray {
        val w = ByteWriter()
        val nPairs = pairs.size

        // Compute binary search fields
        var searchRange = 1
        var entrySelector = 0
        while (searchRange * 2 <= nPairs) {
            searchRange *= 2
            entrySelector++
        }
        searchRange *= 6 // each entry is 6 bytes
        val rangeShift = nPairs * 6 - searchRange

        w.writeU16BE(nPairs)
        w.writeU16BE(searchRange)
        w.writeU16BE(entrySelector)
        w.writeU16BE(rangeShift)

        for (pair in pairs) {
            w.writeU16BE(pair.left)
            w.writeU16BE(pair.right)
            w.writeI16BE(pair.value)
        }

        return w.toByteArray()
    }
}

/**
 * A single kerning pair.
 */
public data class KernPair(
    public val left: Int,
    public val right: Int,
    public val value: Int,
)

/**
 * Fallback for unrecognized kern subtable formats.
 * Stores raw bytes for lossless round-trip.
 */
public class UnknownKernSubtable(
    public var data: ByteArray = ByteArray(0),
) : KernSubtable() {
    override fun compile(): ByteArray = data
}
