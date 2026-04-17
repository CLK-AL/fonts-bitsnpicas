package com.kreative.bitsnpicas.core.truetype

import com.kreative.bitsnpicas.core.ByteReader
import com.kreative.bitsnpicas.core.ByteWriter

/**
 * The `hmtx` table -- horizontal metrics.
 *
 * Contains per-glyph advance widths and left side bearings.
 * The first `hhea.numLongHorMetrics` entries have both advance width
 * and LSB; remaining entries share the last advance width and only
 * store LSB.
 *
 * Dependencies: `hhea` (for numLongHorMetrics), `maxp` (for numGlyphs).
 *
 * Spec: https://docs.microsoft.com/en-us/typography/opentype/spec/hmtx
 */
public class HmtxTable : TrueTypeTable() {

    override val tableName: String get() = "hmtx"

    public val entries: MutableList<HmtxTableEntry> = mutableListOf()

    override val dependencyIds: List<Int>
        get() = listOf(TAG_HHEA, TAG_MAXP)

    override fun compile(dependencies: Map<Int, TrueTypeTable>): ByteArray {
        val hhea = dependencies[TAG_HHEA] as HheaTable
        val numLongHorMetrics = hhea.numLongHorMetrics
        val w = ByteWriter(entries.size * 4)
        for (i in entries.indices) {
            val entry = entries[i]
            if (i < numLongHorMetrics) {
                w.writeU16BE(entry.advanceWidth)
            }
            w.writeI16BE(entry.leftSideBearing)
        }
        return w.toByteArray()
    }

    override fun decompile(data: ByteArray, dependencies: Map<Int, TrueTypeTable>) {
        val hhea = dependencies[TAG_HHEA] as HheaTable
        val maxp = dependencies[TAG_MAXP] as MaxpTable
        val numLongHorMetrics = hhea.numLongHorMetrics
        val numGlyphs = maxp.numGlyphs
        val r = ByteReader(data)
        var lastAdvanceWidth = 0
        entries.clear()
        for (i in 0 until numGlyphs) {
            if (i < numLongHorMetrics) {
                lastAdvanceWidth = r.readU16BE()
            }
            val lsb = r.readI16BE()
            entries.add(HmtxTableEntry(lastAdvanceWidth, lsb))
        }
    }

    public companion object {
        internal const val TAG_HHEA: Int = 0x68686561 // "hhea"
        internal const val TAG_MAXP: Int = 0x6D617870 // "maxp"
    }
}

/**
 * A single horizontal metrics entry: advance width + left side bearing.
 */
public data class HmtxTableEntry(
    public var advanceWidth: Int = 0,
    public var leftSideBearing: Int = 0,
)
