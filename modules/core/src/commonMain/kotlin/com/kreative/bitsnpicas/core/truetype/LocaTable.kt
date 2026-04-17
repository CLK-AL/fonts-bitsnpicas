package com.kreative.bitsnpicas.core.truetype

import com.kreative.bitsnpicas.core.ByteReader
import com.kreative.bitsnpicas.core.ByteWriter

/**
 * The `loca` table -- glyph location index.
 *
 * Stores offsets into the `glyf` table for each glyph.
 * Format is determined by `head.indexToLocFormat`:
 *   0 = short format (offsets / 2 stored as uint16)
 *   1 = long format (offsets stored as uint32)
 *
 * There are `numGlyphs + 1` entries (the extra entry gives
 * the end offset of the last glyph).
 *
 * Dependencies: `head` (for indexToLocFormat), `maxp` (for numGlyphs).
 *
 * Spec: https://docs.microsoft.com/en-us/typography/opentype/spec/loca
 */
public class LocaTable : TrueTypeTable() {

    override val tableName: String get() = "loca"

    public val offsets: MutableList<Int> = mutableListOf()

    override val dependencyIds: List<Int>
        get() = listOf(TAG_HEAD, TAG_MAXP)

    override fun compile(dependencies: Map<Int, TrueTypeTable>): ByteArray {
        val head = dependencies[TAG_HEAD] as HeadTable
        val indexToLocFormat = head.indexToLocFormat
        return when (indexToLocFormat) {
            HeadTable.INDEX_TO_LOC_FORMAT_SHORT -> {
                val w = ByteWriter(offsets.size * 2)
                for (loc in offsets) {
                    w.writeU16BE(loc / 2)
                }
                w.toByteArray()
            }
            HeadTable.INDEX_TO_LOC_FORMAT_LONG -> {
                val w = ByteWriter(offsets.size * 4)
                for (loc in offsets) {
                    w.writeIntBE(loc)
                }
                w.toByteArray()
            }
            else -> error("Invalid indexToLocFormat: $indexToLocFormat")
        }
    }

    override fun decompile(data: ByteArray, dependencies: Map<Int, TrueTypeTable>) {
        val head = dependencies[TAG_HEAD] as HeadTable
        val maxp = dependencies[TAG_MAXP] as MaxpTable
        val indexToLocFormat = head.indexToLocFormat
        val numGlyphs = maxp.numGlyphs
        val r = ByteReader(data)
        offsets.clear()
        when (indexToLocFormat) {
            HeadTable.INDEX_TO_LOC_FORMAT_SHORT -> {
                for (i in 0..numGlyphs) {
                    offsets.add(r.readU16BE() * 2)
                }
            }
            HeadTable.INDEX_TO_LOC_FORMAT_LONG -> {
                for (i in 0..numGlyphs) {
                    offsets.add(r.readIntBE())
                }
            }
            else -> error("Invalid indexToLocFormat: $indexToLocFormat")
        }
    }

    public companion object {
        internal const val TAG_HEAD: Int = 0x68656164 // "head"
        internal const val TAG_MAXP: Int = 0x6D617870 // "maxp"
    }
}
