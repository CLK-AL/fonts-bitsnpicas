package com.kreative.bitsnpicas.core.truetype

import com.kreative.bitsnpicas.core.ByteReader
import com.kreative.bitsnpicas.core.ByteWriter

/**
 * The `glyf` table -- glyph outlines.
 *
 * Stores per-glyph outline data as raw byte arrays.
 * Each entry corresponds to a glyph index; its position
 * in the `glyf` table is determined by the `loca` table.
 *
 * Use [GlyfTableEntry] to parse/emit individual glyph data.
 *
 * Dependencies: `loca` (for glyph offsets).
 *
 * Spec: https://docs.microsoft.com/en-us/typography/opentype/spec/glyf
 */
public class GlyfTable : TrueTypeTable() {

    override val tableName: String get() = "glyf"

    /** Raw byte data for each glyph. Empty arrays represent empty glyphs (e.g. space). */
    public val glyphs: MutableList<ByteArray> = mutableListOf()

    override val dependencyIds: List<Int>
        get() = listOf(TAG_LOCA)

    override fun compile(dependencies: Map<Int, TrueTypeTable>): ByteArray {
        var totalSize = 0
        for (data in glyphs) {
            totalSize += data.size
        }
        val w = ByteWriter(totalSize)
        for (data in glyphs) {
            w.writeBytes(data)
        }
        return w.toByteArray()
    }

    override fun decompile(data: ByteArray, dependencies: Map<Int, TrueTypeTable>) {
        val loca = dependencies[TAG_LOCA] as LocaTable
        val locations = loca.offsets
        glyphs.clear()
        var s = locations[0]
        for (i in 1 until locations.size) {
            val e = locations[i]
            val glyphData = if (e > s && s < data.size) {
                val r = ByteReader(data)
                r.seek(s)
                r.readBytes(e - s)
            } else {
                ByteArray(0)
            }
            glyphs.add(glyphData)
            s = e
        }
    }

    public companion object {
        internal const val TAG_LOCA: Int = 0x6C6F6361 // "loca"
    }
}
