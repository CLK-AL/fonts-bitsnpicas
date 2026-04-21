package com.kreative.bitsnpicas.core.truetype

import com.kreative.bitsnpicas.core.ByteReader
import com.kreative.bitsnpicas.core.ByteWriter

/**
 * The `CPAL` table — Colour Palette table.
 *
 * Stores RGBA colour entries in palettes. Colours are stored as ARGB
 * integers internally (matching the Java source), but serialized in
 * BGRA byte order on disk (per spec: blue, green, red, alpha).
 *
 * Spec: https://docs.microsoft.com/en-us/typography/opentype/spec/cpal
 */
public class CpalTable : TrueTypeTable() {

    override val tableName: String get() = "CPAL"

    public var version: Int = 0
    public var numPaletteEntries: Int = 0
    public var colorRecordIndices: IntArray = IntArray(0)
    public var colorRecordsArray: IntArray = IntArray(0)
    public var paletteTypesArray: IntArray? = null
    public var paletteLabelsArray: IntArray? = null
    public var paletteEntryLabelsArray: IntArray? = null

    override fun compile(dependencies: Map<Int, TrueTypeTable>): ByteArray {
        val w = ByteWriter()
        var offset = colorRecordIndices.size * 2 + 12
        if (version >= 1) offset += 12

        // Version 0 Header
        w.writeU16BE(version)
        w.writeU16BE(numPaletteEntries)
        w.writeU16BE(colorRecordIndices.size)
        w.writeU16BE(colorRecordsArray.size)
        w.writeIntBE(offset) // colorRecordsArrayOffset
        offset += colorRecordsArray.size * 4
        for (i in colorRecordIndices) {
            w.writeU16BE(i)
        }

        // Version 1 Header
        if (version >= 1) {
            val pta = paletteTypesArray
            if (pta != null) {
                w.writeIntBE(offset)
                offset += pta.size * 4
            } else {
                w.writeIntBE(0)
            }
            val pla = paletteLabelsArray
            if (pla != null) {
                w.writeIntBE(offset)
                offset += pla.size * 2
            } else {
                w.writeIntBE(0)
            }
            val pela = paletteEntryLabelsArray
            if (pela != null) {
                w.writeIntBE(offset)
                offset += pela.size * 2
            } else {
                w.writeIntBE(0)
            }
        }

        // Color Records (stored as BGRA on disk = reverseBytes of ARGB)
        for (argb in colorRecordsArray) {
            w.writeIntBE(reverseBytes(argb))
        }

        // Palette Records (version 1)
        if (version >= 1) {
            paletteTypesArray?.let { arr ->
                for (type in arr) w.writeIntBE(type)
            }
            paletteLabelsArray?.let { arr ->
                for (label in arr) w.writeU16BE(label)
            }
            paletteEntryLabelsArray?.let { arr ->
                for (label in arr) w.writeU16BE(label)
            }
        }

        return w.toByteArray()
    }

    override fun decompile(data: ByteArray, dependencies: Map<Int, TrueTypeTable>) {
        val r = ByteReader(data)

        // Version 0 Header
        version = r.readU16BE()
        numPaletteEntries = r.readU16BE()
        val numPalettes = r.readU16BE()
        val numColorRecords = r.readU16BE()
        val colorRecordsArrayOffset = r.readIntBE()

        colorRecordIndices = IntArray(numPalettes)
        for (i in 0 until numPalettes) {
            colorRecordIndices[i] = r.readU16BE()
        }

        // Version 1 Header
        val paletteTypesArrayOffset = if (version < 1) 0 else r.readIntBE()
        val paletteLabelsArrayOffset = if (version < 1) 0 else r.readIntBE()
        val paletteEntryLabelsArrayOffset = if (version < 1) 0 else r.readIntBE()

        // Color Records
        colorRecordsArray = IntArray(numColorRecords)
        r.seek(colorRecordsArrayOffset)
        for (i in 0 until numColorRecords) {
            colorRecordsArray[i] = reverseBytes(r.readIntBE())
        }

        // Palette Records
        if (paletteTypesArrayOffset > 0) {
            r.seek(paletteTypesArrayOffset)
            paletteTypesArray = IntArray(numPalettes) { r.readIntBE() }
        } else {
            paletteTypesArray = null
        }

        if (paletteLabelsArrayOffset > 0) {
            r.seek(paletteLabelsArrayOffset)
            paletteLabelsArray = IntArray(numPalettes) { r.readU16BE() }
        } else {
            paletteLabelsArray = null
        }

        if (paletteEntryLabelsArrayOffset > 0) {
            r.seek(paletteEntryLabelsArrayOffset)
            paletteEntryLabelsArray = IntArray(numColorRecords) { r.readU16BE() }
        } else {
            paletteEntryLabelsArray = null
        }
    }

    public companion object {
        public const val USABLE_WITH_LIGHT_BACKGROUND: Int = 0x0001
        public const val USABLE_WITH_DARK_BACKGROUND: Int = 0x0002

        /** Reverse byte order of a 32-bit integer (equivalent to Integer.reverseBytes). */
        private fun reverseBytes(v: Int): Int =
            ((v and 0xFF) shl 24) or
            ((v shr 8 and 0xFF) shl 16) or
            ((v shr 16 and 0xFF) shl 8) or
            (v shr 24 and 0xFF)
    }
}
