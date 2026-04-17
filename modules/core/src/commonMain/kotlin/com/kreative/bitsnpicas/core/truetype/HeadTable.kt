package com.kreative.bitsnpicas.core.truetype

import com.kreative.bitsnpicas.core.ByteReader
import com.kreative.bitsnpicas.core.ByteWriter

/**
 * The `head` table — font header containing global font metrics,
 * bounding box, date stamps, and format flags.
 *
 * Spec: https://docs.microsoft.com/en-us/typography/opentype/spec/head
 */
public class HeadTable : TrueTypeTable() {

    override val tableName: String get() = "head"

    public var version: Int = VERSION_DEFAULT
    public var fontRevision: Int = 0
    public var checkSum: Int = 0
    public var magicNumber: Int = MAGIC_NUMBER
    public var flags: Int = 0
    public var unitsPerEm: Int = 0
    public var dateCreated: Long = 0L
    public var dateModified: Long = 0L
    public var xMin: Int = 0
    public var yMin: Int = 0
    public var xMax: Int = 0
    public var yMax: Int = 0
    public var macStyle: Int = MAC_STYLE_PLAIN
    public var lowestRecPPEM: Int = 0
    public var fontDirectionHint: Int = FONT_DIRECTION_HINT_MIXED
    public var indexToLocFormat: Int = INDEX_TO_LOC_FORMAT_SHORT
    public var glyphDataFormat: Int = GLYPH_DATA_FORMAT_DEFAULT

    override fun compile(dependencies: Map<Int, TrueTypeTable>): ByteArray {
        val w = ByteWriter(54)
        w.writeIntBE(version)
        w.writeIntBE(fontRevision)
        w.writeIntBE(checkSum)
        w.writeIntBE(magicNumber)
        w.writeU16BE(flags)
        w.writeU16BE(unitsPerEm)
        w.writeLongBE(dateCreated)
        w.writeLongBE(dateModified)
        w.writeI16BE(xMin)
        w.writeI16BE(yMin)
        w.writeI16BE(xMax)
        w.writeI16BE(yMax)
        w.writeU16BE(macStyle)
        w.writeU16BE(lowestRecPPEM)
        w.writeI16BE(fontDirectionHint)
        w.writeI16BE(indexToLocFormat)
        w.writeI16BE(glyphDataFormat)
        return w.toByteArray()
    }

    override fun decompile(data: ByteArray, dependencies: Map<Int, TrueTypeTable>) {
        val r = ByteReader(data)
        version = r.readIntBE()
        fontRevision = r.readIntBE()
        checkSum = r.readIntBE()
        magicNumber = r.readIntBE()
        flags = r.readU16BE()
        unitsPerEm = r.readU16BE()
        dateCreated = r.readLongBE()
        dateModified = r.readLongBE()
        xMin = r.readI16BE()
        yMin = r.readI16BE()
        xMax = r.readI16BE()
        yMax = r.readI16BE()
        macStyle = r.readU16BE()
        lowestRecPPEM = r.readU16BE()
        fontDirectionHint = r.readI16BE()
        indexToLocFormat = r.readI16BE()
        glyphDataFormat = r.readI16BE()
    }

    public companion object {
        public const val VERSION_DEFAULT: Int = 0x00010000
        public const val MAGIC_NUMBER: Int = 0x5F0F3CF5
        public const val MAC_STYLE_PLAIN: Int = 0x00
        public const val MAC_STYLE_BOLD: Int = 0x01
        public const val MAC_STYLE_ITALIC: Int = 0x02
        public const val FONT_DIRECTION_HINT_MIXED: Int = 0
        public const val INDEX_TO_LOC_FORMAT_SHORT: Int = 0
        public const val INDEX_TO_LOC_FORMAT_LONG: Int = 1
        public const val GLYPH_DATA_FORMAT_DEFAULT: Int = 0
        public const val DATE_EPOCH_1904: Long = 2082844800000L
    }
}
