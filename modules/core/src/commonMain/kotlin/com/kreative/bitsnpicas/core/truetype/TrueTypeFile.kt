package com.kreative.bitsnpicas.core.truetype

import com.kreative.bitsnpicas.core.ByteReader
import com.kreative.bitsnpicas.core.ByteWriter

/**
 * Container for a TrueType/OpenType font file.
 *
 * Holds an ordered list of [TrueTypeTable] objects and knows how to
 * [compile] them into a binary font file (offset table + table directory
 * + table data) and [decompile] binary data back into structured tables.
 *
 * Unrecognized table tags are stored as [UnknownTable] and round-trip
 * losslessly.
 */
public class TrueTypeFile {

    public var scaler: Int = SCALER_TRUETYPE
    public val tables: MutableList<TrueTypeTable> = mutableListOf()

    /** Look up a table by its 4-char tag name. */
    public fun getByTableName(name: String): TrueTypeTable? =
        tables.firstOrNull { it.tableName == name }

    /** Look up a table by its 32-bit tag ID. */
    public fun getByTableId(id: Int): TrueTypeTable? =
        tables.firstOrNull { it.tableId == id }

    /**
     * Create the appropriate table instance for a given table ID.
     * Returns [UnknownTable] for any tag not yet ported.
     */
    private fun createTable(tableId: Int): TrueTypeTable {
        return when (tableId) {
            TAG_HEAD -> HeadTable()
            TAG_NAME -> NameTable()
            TAG_POST -> PostTable()
            TAG_CMAP -> CmapTable()
            TAG_OS2  -> Os2Table()
            TAG_HHEA -> HheaTable()
            TAG_HMTX -> HmtxTable()
            TAG_MAXP -> MaxpTable()
            TAG_LOCA -> LocaTable()
            TAG_GLYF -> GlyfTable()
            TAG_COLR -> ColrTable()
            TAG_CPAL -> CpalTable()
            TAG_SVG  -> SvgTable()
            TAG_CBLC -> CblcTable()
            TAG_CBDT -> CbdtTable()
            TAG_KERN -> KernTable()
            TAG_GPOS -> GposTable()
            TAG_GSUB -> GsubTable()
            else -> UnknownTable(tableId)
        }
    }

    /**
     * Compile all tables into a complete TTF binary.
     *
     * Follows the TrueType spec: offset table header, table directory
     * (sorted by tag ID), then table data (padded to 4-byte boundaries).
     * The head table's checkSumAdjustment is computed last.
     */
    public fun compile(): ByteArray {
        val numTables = tables.size
        var searchRange = (1 shl 30)
        while (searchRange > numTables) searchRange = searchRange ushr 1
        val entrySelector = countTrailingZeros(searchRange)
        searchRange = searchRange shl 4
        val rangeShift = (numTables shl 4) - searchRange
        val headerLen = 12 + (numTables shl 4)

        // Build a map for dependency resolution
        val tablesById = mutableMapOf<Int, TrueTypeTable>()
        for (table in tables) {
            tablesById[table.tableId] = table
        }

        // Compile each table and track metadata
        data class TableInfo(
            val table: TrueTypeTable,
            val id: Int,
            var data: ByteArray = ByteArray(0),
            var length: Int = 0,
            var location: Int = 0,
            var checksum: Int = 0,
        )

        val tableInfos = tables.map { TableInfo(it, it.tableId) }
        var currentLocation = headerLen

        for (info in tableInfos) {
            // Resolve dependencies
            val deps = mutableMapOf<Int, TrueTypeTable>()
            for (depId in info.table.dependencyIds) {
                deps[depId] = tablesById[depId]
                    ?: error("Unresolved dependency: ${info.table.tableName} depends on ${TrueTypeTable.idToTag(depId)}")
            }
            info.data = info.table.compile(deps)
            info.length = info.data.size
            info.location = currentLocation

            // Zero out checkSumAdjustment in head table before checksumming
            if (info.id == TAG_HEAD && info.data.size > 11) {
                putInt(info.data, 8, 0)
            }
            info.checksum = chksum(info.data)
            currentLocation += (info.length + 3) and 0x3.inv()
        }

        // Write the file
        val w = ByteWriter(currentLocation)

        // Offset table header
        w.writeIntBE(scaler)
        w.writeU16BE(numTables)
        w.writeU16BE(searchRange)
        w.writeU16BE(entrySelector)
        w.writeU16BE(rangeShift)

        // Table directory entries (sorted by ID)
        val sortedById = tableInfos.sortedBy { it.id }
        for (info in sortedById) {
            w.writeIntBE(info.id)
            w.writeIntBE(info.checksum)
            w.writeIntBE(info.location)
            w.writeIntBE(info.length)
        }

        // Table data (in original order = sorted by location)
        val sortedByLocation = tableInfos.sortedBy { it.location }
        for (info in sortedByLocation) {
            w.writeBytes(info.data)
            val pad = info.length and 0x3
            if (pad > 0) {
                w.writeZeros(4 - pad)
            }
        }

        val result = w.toByteArray()

        // Fix up the head table's checkSumAdjustment
        val headInfo = tableInfos.firstOrNull { it.id == TAG_HEAD }
        if (headInfo != null) {
            val checksumLoc = headInfo.location + 8
            val adjustment = 0xB1B0AFBA.toInt() - chksum(result)
            putInt(result, checksumLoc, adjustment)
        }

        return result
    }

    /**
     * Decompile a TTF binary into structured tables.
     */
    public fun decompile(data: ByteArray) {
        val r = ByteReader(data)

        scaler = r.readIntBE()
        val numTables = r.readU16BE()
        /* searchRange  */ r.readU16BE()
        /* entrySelector */ r.readU16BE()
        /* rangeShift   */ r.readU16BE()

        // Read table directory
        data class TableInfo(
            val id: Int,
            val checksum: Int,
            val location: Int,
            val length: Int,
            var tableData: ByteArray = ByteArray(0),
            var table: TrueTypeTable? = null,
        )

        val tableInfos = mutableListOf<TableInfo>()
        val tableInfoById = mutableMapOf<Int, TableInfo>()

        for (i in 0 until numTables) {
            val id = r.readIntBE()
            val checksum = r.readIntBE()
            val location = r.readIntBE()
            val length = r.readIntBE()
            val info = TableInfo(id, checksum, location, length)
            tableInfos.add(info)
            tableInfoById[id] = info
        }

        // Read raw data for each table
        for (info in tableInfos) {
            r.seek(info.location)
            info.tableData = r.readBytes(info.length)
        }

        // Decompile with dependency resolution (iterative)
        while (true) {
            var tablesDecompiled = 0
            var tablesToDecompile = 0

            for (info in tableInfos) {
                if (info.table == null) {
                    val table = createTable(info.id)
                    val depIds = table.dependencyIds
                    var depsComplete = true
                    val deps = mutableMapOf<Int, TrueTypeTable>()

                    for (depId in depIds) {
                        val depInfo = tableInfoById[depId]
                            ?: error("Unresolved dependency: ${table.tableName} depends on ${TrueTypeTable.idToTag(depId)}")
                        val depTable = depInfo.table
                        if (depTable != null) {
                            deps[depId] = depTable
                        } else {
                            depsComplete = false
                        }
                    }

                    if (depsComplete) {
                        table.decompile(info.tableData, deps)
                        info.table = table
                        tablesDecompiled++
                    } else {
                        tablesToDecompile++
                    }
                }
            }

            if (tablesToDecompile == 0) break
            check(tablesDecompiled > 0) { "Circular dependency detected." }
        }

        // Gather tables in location order
        tables.clear()
        for (info in tableInfos.sortedBy { it.location }) {
            tables.add(info.table!!)
        }
    }

    public companion object {
        public const val SCALER_TRUETYPE: Int = 0x00010000
        public const val SCALER_OPENTYPE: Int = 0x4F54544F

        // Known table tag IDs
        internal const val TAG_HEAD: Int = 0x68656164 // "head"
        internal const val TAG_NAME: Int = 0x6E616D65 // "name"
        internal const val TAG_POST: Int = 0x706F7374 // "post"
        internal const val TAG_CMAP: Int = 0x636D6170 // "cmap"
        internal const val TAG_OS2:  Int = 0x4F532F32 // "OS/2"
        internal const val TAG_HHEA: Int = 0x68686561 // "hhea"
        internal const val TAG_HMTX: Int = 0x686D7478 // "hmtx"
        internal const val TAG_MAXP: Int = 0x6D617870 // "maxp"
        internal const val TAG_LOCA: Int = 0x6C6F6361 // "loca"
        internal const val TAG_GLYF: Int = 0x676C7966 // "glyf"
        internal const val TAG_COLR: Int = 0x434F4C52 // "COLR"
        internal const val TAG_CPAL: Int = 0x4350414C // "CPAL"
        internal const val TAG_SVG:  Int = 0x53564720 // "SVG "
        internal const val TAG_CBLC: Int = 0x43424C43 // "CBLC"
        internal const val TAG_CBDT: Int = 0x43424454 // "CBDT"
        internal const val TAG_KERN: Int = 0x6B65726E // "kern"
        internal const val TAG_GPOS: Int = 0x47504F53 // "GPOS"
        internal const val TAG_GSUB: Int = 0x47535542 // "GSUB"

        private fun chksum(data: ByteArray): Int {
            var sum = 0
            val nl = data.size and 0x3.inv()
            var i = 0
            while (i < nl) {
                sum += getInt(data, i)
                i += 4
            }
            var s = 24
            while (i < data.size) {
                sum += (data[i].toInt() and 0xFF) shl s
                i++
                s -= 8
            }
            return sum
        }

        private fun getInt(data: ByteArray, i: Int): Int =
            ((data[i].toInt() and 0xFF) shl 24) or
            ((data[i + 1].toInt() and 0xFF) shl 16) or
            ((data[i + 2].toInt() and 0xFF) shl 8) or
            (data[i + 3].toInt() and 0xFF)

        private fun putInt(data: ByteArray, i: Int, v: Int) {
            data[i] = ((v shr 24) and 0xFF).toByte()
            data[i + 1] = ((v shr 16) and 0xFF).toByte()
            data[i + 2] = ((v shr 8) and 0xFF).toByte()
            data[i + 3] = (v and 0xFF).toByte()
        }

        /** Count trailing zeros in an integer (equivalent to Integer.numberOfTrailingZeros). */
        private fun countTrailingZeros(value: Int): Int {
            if (value == 0) return 32
            var v = value
            var n = 0
            while (v and 1 == 0) {
                n++
                v = v ushr 1
            }
            return n
        }
    }
}
