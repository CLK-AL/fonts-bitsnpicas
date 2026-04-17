package com.kreative.bitsnpicas.core.truetype

import com.kreative.bitsnpicas.core.ByteReader
import com.kreative.bitsnpicas.core.ByteWriter

/**
 * The `OS/2` table -- OS/2 and Windows metrics.
 *
 * Variable-length table whose size depends on the version.
 * Apple and Microsoft historically disagreed on field counts
 * before version 3, so the `length` field tracks the actual
 * binary length to control which fields to emit on compile.
 *
 * Spec: https://docs.microsoft.com/en-us/typography/opentype/spec/os2
 */
public class Os2Table : TrueTypeTable() {

    override val tableName: String get() = "OS/2"

    // Apple version 0 (68-byte) fields
    public var length: Int = LENGTH_MAX
    public var version: Int = VERSION_MAX
    public var averageCharWidth: Int = 0
    public var weightClass: Int = WEIGHT_CLASS_MEDIUM
    public var widthClass: Int = WIDTH_CLASS_MEDIUM
    public var flags: Int = 0
    public var subscriptXSize: Int = 0
    public var subscriptYSize: Int = 0
    public var subscriptXOffset: Int = 0
    public var subscriptYOffset: Int = 0
    public var superscriptXSize: Int = 0
    public var superscriptYSize: Int = 0
    public var superscriptXOffset: Int = 0
    public var superscriptYOffset: Int = 0
    public var strikeoutWidth: Int = 0
    public var strikeoutPosition: Int = 0
    public var familyClass: Int = 0
    public var familySubClass: Int = 0
    public var panoseFamilyType: Int = 0
    public var panoseSerifStyle: Int = 0
    public var panoseWeight: Int = 0
    public var panoseProportion: Int = 0
    public var panoseContrast: Int = 0
    public var panoseStrokeVariation: Int = 0
    public var panoseArmStyle: Int = 0
    public var panoseLetterform: Int = 0
    public var panoseMidline: Int = 0
    public var panoseXHeight: Int = 0
    public val unicodeRanges: IntArray = IntArray(4)
    public var vendorID: Int = VENDOR_ID_ckbt
    public var fsSelection: Int = 0
    public var fsFirstCharIndex: Int = 0x20
    public var fsLastCharIndex: Int = 0x20

    // Apple version 1, Microsoft version 0 (78-byte) fields
    public var typoAscent: Int = 0
    public var typoDescent: Int = 0
    public var typoLineGap: Int = 0
    public var winAscent: Int = 0
    public var winDescent: Int = 0

    // Apple version 2, Microsoft version 1 (86-byte) fields
    public val codePages: IntArray = IntArray(2)

    // Microsoft version 2, version 3 or 4 (96-byte) fields
    public var xHeight: Int = 0
    public var capHeight: Int = 0
    public var defaultChar: Int = 0
    public var breakChar: Int = 0x20
    public var maxContext: Int = 0

    // Version 5 (100-byte) fields
    public var lowerOpticalPointSize: Int = 0
    public var upperOpticalPointSize: Int = 0xFFFF

    override fun compile(dependencies: Map<Int, TrueTypeTable>): ByteArray {
        val w = ByteWriter(length)
        w.writeI16BE(version)
        w.writeI16BE(averageCharWidth)
        w.writeU16BE(weightClass)
        w.writeU16BE(widthClass)
        w.writeU16BE(flags)
        w.writeI16BE(subscriptXSize)
        w.writeI16BE(subscriptYSize)
        w.writeI16BE(subscriptXOffset)
        w.writeI16BE(subscriptYOffset)
        w.writeI16BE(superscriptXSize)
        w.writeI16BE(superscriptYSize)
        w.writeI16BE(superscriptXOffset)
        w.writeI16BE(superscriptYOffset)
        w.writeI16BE(strikeoutWidth)
        w.writeI16BE(strikeoutPosition)
        w.writeU8(familyClass)
        w.writeU8(familySubClass)
        w.writeU8(panoseFamilyType)
        w.writeU8(panoseSerifStyle)
        w.writeU8(panoseWeight)
        w.writeU8(panoseProportion)
        w.writeU8(panoseContrast)
        w.writeU8(panoseStrokeVariation)
        w.writeU8(panoseArmStyle)
        w.writeU8(panoseLetterform)
        w.writeU8(panoseMidline)
        w.writeU8(panoseXHeight)
        w.writeIntBE(unicodeRanges[0])
        w.writeIntBE(unicodeRanges[1])
        w.writeIntBE(unicodeRanges[2])
        w.writeIntBE(unicodeRanges[3])
        w.writeIntBE(vendorID)
        w.writeU16BE(fsSelection)
        w.writeU16BE(fsFirstCharIndex)
        w.writeU16BE(fsLastCharIndex)
        if (length >= LENGTH_78) {
            w.writeI16BE(typoAscent)
            w.writeI16BE(typoDescent)
            w.writeI16BE(typoLineGap)
            w.writeI16BE(winAscent)
            w.writeI16BE(winDescent)
        }
        if (length >= LENGTH_86) {
            w.writeIntBE(codePages[0])
            w.writeIntBE(codePages[1])
        }
        if (length >= LENGTH_96) {
            w.writeI16BE(xHeight)
            w.writeI16BE(capHeight)
            w.writeU16BE(defaultChar)
            w.writeU16BE(breakChar)
            w.writeU16BE(maxContext)
        }
        if (length >= LENGTH_100) {
            w.writeU16BE(lowerOpticalPointSize)
            w.writeU16BE(upperOpticalPointSize)
        }
        return w.toByteArray()
    }

    override fun decompile(data: ByteArray, dependencies: Map<Int, TrueTypeTable>) {
        val r = ByteReader(data)
        length = data.size
        version = r.readU16BE()
        averageCharWidth = r.readI16BE()
        weightClass = r.readU16BE()
        widthClass = r.readU16BE()
        flags = r.readU16BE()
        subscriptXSize = r.readI16BE()
        subscriptYSize = r.readI16BE()
        subscriptXOffset = r.readI16BE()
        subscriptYOffset = r.readI16BE()
        superscriptXSize = r.readI16BE()
        superscriptYSize = r.readI16BE()
        superscriptXOffset = r.readI16BE()
        superscriptYOffset = r.readI16BE()
        strikeoutWidth = r.readI16BE()
        strikeoutPosition = r.readI16BE()
        familyClass = r.readU8()
        familySubClass = r.readU8()
        panoseFamilyType = r.readU8()
        panoseSerifStyle = r.readU8()
        panoseWeight = r.readU8()
        panoseProportion = r.readU8()
        panoseContrast = r.readU8()
        panoseStrokeVariation = r.readU8()
        panoseArmStyle = r.readU8()
        panoseLetterform = r.readU8()
        panoseMidline = r.readU8()
        panoseXHeight = r.readU8()
        unicodeRanges[0] = r.readIntBE()
        unicodeRanges[1] = r.readIntBE()
        unicodeRanges[2] = r.readIntBE()
        unicodeRanges[3] = r.readIntBE()
        vendorID = r.readIntBE()
        fsSelection = r.readU16BE()
        fsFirstCharIndex = r.readU16BE()
        fsLastCharIndex = r.readU16BE()
        typoAscent = if (length >= LENGTH_78) r.readI16BE() else 0
        typoDescent = if (length >= LENGTH_78) r.readI16BE() else 0
        typoLineGap = if (length >= LENGTH_78) r.readI16BE() else 0
        winAscent = if (length >= LENGTH_78) r.readI16BE() else 0
        winDescent = if (length >= LENGTH_78) r.readI16BE() else 0
        codePages[0] = if (length >= LENGTH_86) r.readIntBE() else 0
        codePages[1] = if (length >= LENGTH_86) r.readIntBE() else 0
        xHeight = if (length >= LENGTH_96) r.readI16BE() else 0
        capHeight = if (length >= LENGTH_96) r.readI16BE() else 0
        defaultChar = if (length >= LENGTH_96) r.readU16BE() else 0
        breakChar = if (length >= LENGTH_96) r.readU16BE() else 0x20
        maxContext = if (length >= LENGTH_96) r.readU16BE() else 0
        lowerOpticalPointSize = if (length >= LENGTH_100) r.readU16BE() else 0
        upperOpticalPointSize = if (length >= LENGTH_100) r.readU16BE() else 0xFFFF
    }

    public companion object {
        public const val LENGTH_68: Int = 68
        public const val LENGTH_78: Int = 78
        public const val LENGTH_86: Int = 86
        public const val LENGTH_96: Int = 96
        public const val LENGTH_100: Int = 100
        public const val LENGTH_MAX: Int = 100

        public const val VERSION_MAX: Int = 5

        public const val WEIGHT_CLASS_MEDIUM: Int = 500
        public const val WIDTH_CLASS_MEDIUM: Int = 5

        public const val FS_SELECTION_ITALIC: Int = 0x0001
        public const val FS_SELECTION_BOLD: Int = 0x0020
        public const val FS_SELECTION_REGULAR: Int = 0x0040
        public const val FS_SELECTION_USE_TYPO_METRICS: Int = 0x0080

        public const val VENDOR_ID_ckbt: Int = 0x636B6274
    }
}
