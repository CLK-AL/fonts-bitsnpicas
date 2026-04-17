package com.kreative.bitsnpicas.core.truetype

import com.kreative.bitsnpicas.core.ByteReader
import com.kreative.bitsnpicas.core.ByteWriter

/**
 * A single entry in the cmap table directory, associating a
 * (platformID, platformSpecificID) pair with a [CmapSubtable].
 */
public class CmapTableEntry(
    public var platformID: Int = 0,
    public var platformSpecificID: Int = 0,
    public var subtable: CmapSubtable? = null,
)

/**
 * The `cmap` table -- character-to-glyph mapping.
 *
 * Contains one or more subtables, each identified by a
 * (platformID, platformSpecificID) pair. Multiple entries
 * can share the same subtable (by offset).
 *
 * Spec: https://docs.microsoft.com/en-us/typography/opentype/spec/cmap
 */
public class CmapTable : TrueTypeTable() {

    override val tableName: String get() = "cmap"

    public var version: Int = 0
    public val entries: MutableList<CmapTableEntry> = mutableListOf()
    public val subtables: MutableList<CmapSubtable> = mutableListOf()

    public fun getSubtable(platformID: Int, platformSpecificID: Int): CmapSubtable? {
        for (e in entries) {
            if (e.platformID == platformID && e.platformSpecificID == platformSpecificID) {
                return e.subtable
            }
        }
        return null
    }

    public fun getBestSubtable(): CmapSubtable? {
        // Try Unicode platform subtables (best to worst)
        getSubtable(PLATFORM_ID_UNICODE, 10)?.let { return it }  // FontForge
        getSubtable(PLATFORM_ID_UNICODE, 6)?.let { return it }   // Full
        getSubtable(PLATFORM_ID_UNICODE, 4)?.let { return it }   // 2.0 non-BMP
        getSubtable(PLATFORM_ID_UNICODE, 3)?.let { return it }   // 2.0
        getSubtable(PLATFORM_ID_UNICODE, 1)?.let { return it }   // 1.1
        getSubtable(PLATFORM_ID_UNICODE, 0)?.let { return it }   // Default
        // Try Windows platform
        getSubtable(PLATFORM_ID_WINDOWS, 10)?.let { return it }  // Unicode 32
        getSubtable(PLATFORM_ID_WINDOWS, 1)?.let { return it }   // Unicode 16
        return null
    }

    public fun getGlyphIndex(charCode: Int): Int {
        val s = getBestSubtable() ?: return 0
        return s.getGlyphIndex(charCode)
    }

    override fun compile(dependencies: Map<Int, TrueTypeTable>): ByteArray {
        val w = ByteWriter(1024)
        w.writeU16BE(version)
        w.writeU16BE(entries.size)

        // Compile each subtable and track offsets
        var currentOffset = 4 + entries.size * 8
        val subtableData = mutableMapOf<CmapSubtable, ByteArray>()
        val subtableOffset = mutableMapOf<CmapSubtable, Int>()
        for (subtable in subtables) {
            val data = subtable.compile()
            subtableData[subtable] = data
            subtableOffset[subtable] = currentOffset
            currentOffset += data.size
        }

        // Write entry headers
        for (e in entries) {
            w.writeU16BE(e.platformID)
            w.writeU16BE(e.platformSpecificID)
            w.writeIntBE(subtableOffset[e.subtable] ?: 0)
        }

        // Write subtable data
        for (subtable in subtables) {
            w.writeBytes(subtableData[subtable]!!)
        }

        return w.toByteArray()
    }

    override fun decompile(data: ByteArray, dependencies: Map<Int, TrueTypeTable>) {
        val r = ByteReader(data)
        version = r.readU16BE()
        val numSubtables = r.readU16BE()

        // Read entry headers
        data class SubtableInfo(
            val platformID: Int,
            val platformSpecificID: Int,
            val offset: Int,
        )

        val subtableInfos = mutableListOf<SubtableInfo>()
        val uniqueOffsets = mutableSetOf<Int>()
        uniqueOffsets.add(data.size) // sentinel for end

        for (i in 0 until numSubtables) {
            val platformID = r.readU16BE()
            val platformSpecificID = r.readU16BE()
            val offset = r.readIntBE()
            subtableInfos.add(SubtableInfo(platformID, platformSpecificID, offset))
            uniqueOffsets.add(offset)
        }

        val sortedOffsets = uniqueOffsets.sorted()

        // Extract raw data for each unique offset
        val offsetToData = mutableMapOf<Int, ByteArray>()
        for (i in 0 until sortedOffsets.size - 1) {
            val s = sortedOffsets[i]
            val e = sortedOffsets[i + 1]
            if (s < data.size && e <= data.size) {
                val subReader = ByteReader(data)
                subReader.seek(s)
                offsetToData[s] = subReader.readBytes(e - s)
            }
        }

        // Decompile each unique subtable
        val offsetToSubtable = mutableMapOf<Int, CmapSubtable>()
        for ((offset, subData) in offsetToData) {
            if (subData.size < 2) continue
            val fmt = ((subData[0].toInt() and 0xFF) shl 8) or (subData[1].toInt() and 0xFF)
            val subtable = createSubtable(fmt)
            subtable.decompile(subData)
            offsetToSubtable[offset] = subtable
        }

        // Build entries list
        entries.clear()
        for (info in subtableInfos) {
            val entry = CmapTableEntry(
                platformID = info.platformID,
                platformSpecificID = info.platformSpecificID,
                subtable = offsetToSubtable[info.offset],
            )
            entries.add(entry)
        }

        // Build ordered subtables list
        subtables.clear()
        for (i in 0 until sortedOffsets.size - 1) {
            val offset = sortedOffsets[i]
            val sub = offsetToSubtable[offset]
            if (sub != null) subtables.add(sub)
        }
    }

    public companion object {
        public const val PLATFORM_ID_UNICODE: Int = 0
        public const val PLATFORM_ID_MACINTOSH: Int = 1
        public const val PLATFORM_ID_WINDOWS: Int = 3

        public fun createSubtable(format: Int): CmapSubtable = when (format) {
            0 -> CmapSubtableFormat0()
            4 -> CmapSubtableFormat4()
            6 -> CmapSubtableFormat6()
            10 -> CmapSubtableFormat10()
            12 -> CmapSubtableFormat12()
            else -> UnknownCmapSubtable(format)
        }
    }
}
