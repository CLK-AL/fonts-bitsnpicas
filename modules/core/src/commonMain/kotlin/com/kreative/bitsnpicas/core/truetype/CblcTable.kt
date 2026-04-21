package com.kreative.bitsnpicas.core.truetype

import com.kreative.bitsnpicas.core.ByteReader
import com.kreative.bitsnpicas.core.ByteWriter

/**
 * The `CBLC` table — Colour Bitmap Location table.
 *
 * Stores an index of bitmap sizes, each containing index subtables
 * that map glyph IDs to offsets in the companion [CbdtTable].
 *
 * This is a simplified port that preserves the table structure for
 * lossless round-trip. Individual index subtable formats (1-5) are
 * stored as raw bytes rather than fully parsed, since the primary
 * use case is preserving existing CBLC data during font manipulation.
 *
 * The full EBLC/CBLC hierarchy (EblcBitmapSize, EblcIndexSubtable
 * formats 1-5, SbitLineMetrics, etc.) is deferred to a follow-up.
 *
 * Spec: https://docs.microsoft.com/en-us/typography/opentype/spec/cblc
 */
public class CblcTable : TrueTypeTable() {

    override val tableName: String get() = "CBLC"

    public var version: Int = 0x00030000
    public val bitmapSizes: MutableList<BitmapSize> = mutableListOf()

    override fun compile(dependencies: Map<Int, TrueTypeTable>): ByteArray {
        val w = ByteWriter()
        w.writeIntBE(version)
        w.writeIntBE(bitmapSizes.size)

        // Calculate offsets: header is 8 bytes, then numSizes * 48 bytes for BitmapSize records
        val headerLen = 8 + bitmapSizes.size * 48
        var subtableDataOffset = headerLen

        // First pass: compute indexSubTableArrayOffset for each BitmapSize
        // and the total size of the subtable data blocks
        data class SizeInfo(
            val bs: BitmapSize,
            val subtableArrayOffset: Int,
            val subtableDataBytes: ByteArray,
        )

        val sizeInfos = mutableListOf<SizeInfo>()
        for (bs in bitmapSizes) {
            sizeInfos.add(SizeInfo(bs, subtableDataOffset, bs.rawSubtableData))
            subtableDataOffset += bs.rawSubtableData.size
        }

        // Write BitmapSize records
        for (info in sizeInfos) {
            val bs = info.bs
            w.writeIntBE(info.subtableArrayOffset) // indexSubTableArrayOffset
            w.writeIntBE(info.subtableDataBytes.size) // indexTablesSize
            w.writeIntBE(bs.numberOfIndexSubTables)
            w.writeIntBE(bs.colorRef)
            // hori SbitLineMetrics (12 bytes)
            w.writeBytes(bs.horiLineMetrics)
            // vert SbitLineMetrics (12 bytes)
            w.writeBytes(bs.vertLineMetrics)
            w.writeU16BE(bs.startGlyphIndex)
            w.writeU16BE(bs.endGlyphIndex)
            w.writeU8(bs.ppemX)
            w.writeU8(bs.ppemY)
            w.writeU8(bs.bitDepth)
            w.writeU8(bs.flags)
        }

        // Write subtable data blocks
        for (info in sizeInfos) {
            w.writeBytes(info.subtableDataBytes)
        }

        return w.toByteArray()
    }

    override fun decompile(data: ByteArray, dependencies: Map<Int, TrueTypeTable>) {
        val r = ByteReader(data)
        version = r.readIntBE()
        val numSizes = r.readIntBE()

        data class SizeHeader(
            val indexSubTableArrayOffset: Int,
            val indexTablesSize: Int,
            val numberOfIndexSubTables: Int,
            val colorRef: Int,
            val horiLineMetrics: ByteArray,
            val vertLineMetrics: ByteArray,
            val startGlyphIndex: Int,
            val endGlyphIndex: Int,
            val ppemX: Int,
            val ppemY: Int,
            val bitDepth: Int,
            val flags: Int,
        )

        val headers = mutableListOf<SizeHeader>()
        for (i in 0 until numSizes) {
            val istaOffset = r.readIntBE()
            val iSize = r.readIntBE()
            val numIST = r.readIntBE()
            val colorRef = r.readIntBE()
            val hori = r.readBytes(12) // SbitLineMetrics
            val vert = r.readBytes(12) // SbitLineMetrics
            val startGlyph = r.readU16BE()
            val endGlyph = r.readU16BE()
            val ppemX = r.readU8()
            val ppemY = r.readU8()
            val bitDepth = r.readU8()
            val flags = r.readU8()
            headers.add(SizeHeader(
                istaOffset, iSize, numIST, colorRef,
                hori, vert, startGlyph, endGlyph,
                ppemX, ppemY, bitDepth, flags
            ))
        }

        bitmapSizes.clear()
        for (h in headers) {
            val bs = BitmapSize()
            bs.numberOfIndexSubTables = h.numberOfIndexSubTables
            bs.colorRef = h.colorRef
            bs.horiLineMetrics = h.horiLineMetrics
            bs.vertLineMetrics = h.vertLineMetrics
            bs.startGlyphIndex = h.startGlyphIndex
            bs.endGlyphIndex = h.endGlyphIndex
            bs.ppemX = h.ppemX
            bs.ppemY = h.ppemY
            bs.bitDepth = h.bitDepth
            bs.flags = h.flags

            // Read raw subtable data block
            if (h.indexTablesSize > 0) {
                r.seek(h.indexSubTableArrayOffset)
                bs.rawSubtableData = r.readBytes(h.indexTablesSize)
            } else {
                bs.rawSubtableData = ByteArray(0)
            }

            bitmapSizes.add(bs)
        }
    }

    /**
     * A bitmap strike size record.
     *
     * Line metrics are stored as raw 12-byte arrays (SbitLineMetrics)
     * for lossless round-trip. Index subtable data is stored as a raw
     * byte block for the same reason.
     */
    public class BitmapSize {
        public var numberOfIndexSubTables: Int = 0
        public var colorRef: Int = 0
        public var horiLineMetrics: ByteArray = ByteArray(12)
        public var vertLineMetrics: ByteArray = ByteArray(12)
        public var startGlyphIndex: Int = 0
        public var endGlyphIndex: Int = 0
        public var ppemX: Int = 0
        public var ppemY: Int = 0
        public var bitDepth: Int = 0
        public var flags: Int = 0
        /** Raw index subtable array + subtable data for this strike. */
        public var rawSubtableData: ByteArray = ByteArray(0)
    }
}
