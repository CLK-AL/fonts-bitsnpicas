package com.kreative.bitsnpicas.core.truetype

import com.kreative.bitsnpicas.core.ByteReader
import com.kreative.bitsnpicas.core.ByteWriter

/**
 * The `name` table — contains human-readable name strings for the font
 * (family name, style, copyright, etc.).
 *
 * Entries are stored as a mutable list of [NameTableEntry].
 */
public class NameTable : TrueTypeTable() {

    override val tableName: String get() = "name"

    public var format: Int = FORMAT_DEFAULT
    public val entries: MutableList<NameTableEntry> = mutableListOf()

    override fun compile(dependencies: Map<Int, TrueTypeTable>): ByteArray {
        val w = ByteWriter(256)
        w.writeU16BE(format)
        w.writeU16BE(entries.size)
        val stringDataOffset = 6 + entries.size * 12
        w.writeU16BE(stringDataOffset)

        // Build entry info with data locations
        data class EntryInfo(val entry: NameTableEntry, var location: Int)

        val infos = mutableListOf<EntryInfo>()
        var currentLocation = 0
        for (e in entries) {
            infos.add(EntryInfo(e, currentLocation))
            currentLocation += e.nameData.size
            if (e.padding in 1..4) {
                currentLocation += e.padding
            }
        }

        // Write name records sorted by compareTo (index, platform, encoding, language, nameID)
        val sortedByNameId = infos.sortedBy { it.entry }
        for (info in sortedByNameId) {
            w.writeU16BE(info.entry.platformID)
            w.writeU16BE(info.entry.platformSpecificID)
            w.writeU16BE(info.entry.languageID)
            w.writeU16BE(info.entry.nameID)
            w.writeU16BE(info.entry.nameData.size)
            w.writeU16BE(info.location)
        }

        // Write string data in location order
        val sortedByLocation = infos.sortedBy { it.location }
        for (info in sortedByLocation) {
            w.writeBytes(info.entry.nameData)
            if (info.entry.padding in 1..4) {
                w.writeZeros(info.entry.padding)
            }
        }

        return w.toByteArray()
    }

    override fun decompile(data: ByteArray, dependencies: Map<Int, TrueTypeTable>) {
        val r = ByteReader(data)
        format = r.readU16BE()
        val count = r.readU16BE()
        val stringOffset = r.readU16BE()

        data class EntryInfo(val entry: NameTableEntry, val location: Int, val dataLength: Int)

        val infos = mutableListOf<EntryInfo>()
        for (i in 0 until count) {
            val entry = NameTableEntry()
            entry.index = i
            entry.platformID = r.readU16BE()
            entry.platformSpecificID = r.readU16BE()
            entry.languageID = r.readU16BE()
            entry.nameID = r.readU16BE()
            val dataLength = r.readU16BE()
            val loc = stringOffset + r.readU16BE()
            entry.nameData = ByteArray(dataLength)
            infos.add(EntryInfo(entry, loc, dataLength))
        }

        // Read string data for each entry
        for (info in infos) {
            r.seek(info.location)
            info.entry.nameData = r.readBytes(info.dataLength)
        }

        // Calculate padding by sorting by location
        val sortedByLocation = infos.sortedBy { it.location }
        for (i in sortedByLocation.indices) {
            val info = sortedByLocation[i]
            val nextLocation = if (i + 1 < sortedByLocation.size) {
                sortedByLocation[i + 1].location
            } else {
                data.size
            }
            info.entry.padding = nextLocation - info.location - info.entry.nameData.size
        }

        entries.clear()
        for (info in sortedByLocation) {
            entries.add(info.entry)
        }
    }

    public companion object {
        public const val FORMAT_DEFAULT: Int = 0
    }
}
