package com.kreative.bitsnpicas.core.truetype

import com.kreative.bitsnpicas.core.ByteReader
import com.kreative.bitsnpicas.core.ByteWriter

/**
 * Base class for cmap subtable formats.
 *
 * Each subtable maps character codes to glyph indices using a
 * particular format (0, 4, 6, 10, 12, etc.).
 *
 * Spec: https://docs.microsoft.com/en-us/typography/opentype/spec/cmap
 */
public abstract class CmapSubtable {

    /** The format number for this subtable. */
    public abstract val format: Int

    /** Look up the glyph index for the given character code. Returns 0 if not mapped. */
    public abstract fun getGlyphIndex(charCode: Int): Int

    /** Compile this subtable to raw bytes. */
    public abstract fun compile(): ByteArray

    /** Decompile raw bytes into this subtable's structured fields. */
    public abstract fun decompile(data: ByteArray)
}

// ---- Subtable entry types ----

/**
 * Base class for cmap subtable range entries used by formats 4 and 12.
 */
public abstract class CmapSubtableEntry {
    public var startCharCode: Int = 0
    public var endCharCode: Int = 0

    public fun contains(charCode: Int): Boolean =
        charCode in startCharCode..endCharCode

    public abstract fun getGlyphIndex(charCode: Int): Int
}

/**
 * A sequential entry: glyphs are mapped by offset from startCharCode.
 * Used by format 4 (for idRangeOffset=0 segments) and format 12.
 */
public class CmapSubtableSequentialEntry : CmapSubtableEntry() {
    public var glyphIndex: Int = 0

    override fun getGlyphIndex(charCode: Int): Int =
        glyphIndex + (charCode - startCharCode)
}

/**
 * A random-access entry: glyphs are mapped by an explicit array.
 * Used by format 4 (for idRangeOffset!=0 segments).
 */
public class CmapSubtableRandomEntry : CmapSubtableEntry() {
    public var glyphIndex: IntArray = IntArray(0)

    override fun getGlyphIndex(charCode: Int): Int =
        glyphIndex[charCode - startCharCode]
}

// ---- Subtable format implementations ----

/**
 * Cmap subtable format 0 -- byte encoding table.
 * Maps character codes 0..255 via a 256-byte lookup table.
 */
public class CmapSubtableFormat0 : CmapSubtable() {
    override val format: Int get() = 0
    public var languageID: Int = 0
    public val glyphIndex: IntArray = IntArray(256)

    override fun getGlyphIndex(charCode: Int): Int =
        if (charCode in 0..255) glyphIndex[charCode] else 0

    override fun compile(): ByteArray {
        val w = ByteWriter(262)
        w.writeU16BE(0)   // format
        w.writeU16BE(262) // length
        w.writeU16BE(languageID)
        for (i in 0 until 256) {
            w.writeU8(glyphIndex[i])
        }
        return w.toByteArray()
    }

    override fun decompile(data: ByteArray) {
        val r = ByteReader(data)
        r.readU16BE() // format
        r.readU16BE() // length
        languageID = r.readU16BE()
        for (i in 0 until 256) {
            glyphIndex[i] = r.readU8()
        }
    }
}

/**
 * Cmap subtable format 4 -- segment mapping to delta values.
 * The most common BMP-only format used by Windows fonts.
 */
public class CmapSubtableFormat4 : CmapSubtable() {
    override val format: Int get() = 4
    public var languageID: Int = 0
    public val entries: MutableList<CmapSubtableEntry> = mutableListOf()

    override fun getGlyphIndex(charCode: Int): Int {
        for (e in entries) {
            if (e.contains(charCode)) return e.getGlyphIndex(charCode)
        }
        return 0
    }

    override fun compile(): ByteArray {
        val segCnt = entries.size
        var searchRange = (1 shl 30)
        while (searchRange > segCnt) searchRange = searchRange ushr 1
        val entrySelector = countTrailingZeros(searchRange)
        searchRange = searchRange shl 1
        val rangeShift = (segCnt shl 1) - searchRange
        var fmt4len = 16 + segCnt * 8
        for (e in entries) {
            if (e is CmapSubtableRandomEntry) {
                fmt4len += e.glyphIndex.size * 2
            }
        }

        val w = ByteWriter(fmt4len)
        w.writeU16BE(4) // format
        w.writeU16BE(fmt4len)
        w.writeU16BE(languageID)
        w.writeU16BE(segCnt * 2)
        w.writeU16BE(searchRange)
        w.writeU16BE(entrySelector)
        w.writeU16BE(rangeShift)
        for (e in entries) w.writeU16BE(e.endCharCode)
        w.writeU16BE(0) // reservedPad
        for (e in entries) w.writeU16BE(e.startCharCode)
        for (e in entries) {
            if (e is CmapSubtableSequentialEntry) {
                w.writeU16BE((e.glyphIndex - e.startCharCode) and 0xFFFF)
            } else {
                w.writeU16BE(0)
            }
        }
        var idRangeOffset = entries.size
        for (e in entries) {
            if (e is CmapSubtableRandomEntry) {
                w.writeU16BE(idRangeOffset * 2)
                idRangeOffset += e.glyphIndex.size
            } else {
                w.writeU16BE(0)
            }
            idRangeOffset--
        }
        for (e in entries) {
            if (e is CmapSubtableRandomEntry) {
                for (gi in e.glyphIndex) {
                    w.writeU16BE(gi)
                }
            }
        }
        return w.toByteArray()
    }

    override fun decompile(data: ByteArray) {
        val r = ByteReader(data)
        r.readU16BE() // format
        r.readU16BE() // length
        languageID = r.readU16BE()
        val segCnt = r.readU16BE() / 2
        r.readU16BE() // searchRange
        r.readU16BE() // entrySelector
        r.readU16BE() // rangeShift

        val endCodes = IntArray(segCnt)
        for (i in 0 until segCnt) endCodes[i] = r.readU16BE()
        r.readU16BE() // reservedPad
        val startCodes = IntArray(segCnt)
        for (i in 0 until segCnt) startCodes[i] = r.readU16BE()
        val idDeltas = IntArray(segCnt)
        for (i in 0 until segCnt) idDeltas[i] = r.readU16BE()

        // Mark position just before idRangeOffset array
        val idRangeOffsetStart = r.pos
        val idRangeOffsets = IntArray(segCnt)
        for (i in 0 until segCnt) idRangeOffsets[i] = r.readU16BE()

        entries.clear()
        for (i in 0 until segCnt) {
            if (idRangeOffsets[i] == 0) {
                val e = CmapSubtableSequentialEntry()
                e.startCharCode = startCodes[i]
                e.endCharCode = endCodes[i]
                e.glyphIndex = (startCodes[i] + idDeltas[i]) and 0xFFFF
                entries.add(e)
            } else {
                val e = CmapSubtableRandomEntry()
                e.startCharCode = startCodes[i]
                e.endCharCode = endCodes[i]
                e.glyphIndex = IntArray(e.endCharCode - e.startCharCode + 1)
                // Seek relative to the idRangeOffset entry position for this segment
                val seekPos = idRangeOffsetStart + i * 2 + idRangeOffsets[i]
                r.seek(seekPos)
                for (j in e.glyphIndex.indices) {
                    e.glyphIndex[j] = r.readU16BE()
                }
                entries.add(e)
            }
        }
    }

    private fun countTrailingZeros(value: Int): Int {
        if (value == 0) return 32
        var v = value
        var n = 0
        while (v and 1 == 0) { n++; v = v ushr 1 }
        return n
    }
}

/**
 * Cmap subtable format 6 -- trimmed table mapping.
 */
public class CmapSubtableFormat6 : CmapSubtable() {
    override val format: Int get() = 6
    public var languageID: Int = 0
    public var firstChar: Int = 0
    public var glyphIndex: IntArray = IntArray(0)

    override fun getGlyphIndex(charCode: Int): Int =
        if (charCode >= firstChar && charCode < firstChar + glyphIndex.size)
            glyphIndex[charCode - firstChar]
        else 0

    override fun compile(): ByteArray {
        val w = ByteWriter(10 + glyphIndex.size * 2)
        w.writeU16BE(6)
        w.writeU16BE(10 + glyphIndex.size * 2)
        w.writeU16BE(languageID)
        w.writeU16BE(firstChar)
        w.writeU16BE(glyphIndex.size)
        for (gi in glyphIndex) w.writeU16BE(gi)
        return w.toByteArray()
    }

    override fun decompile(data: ByteArray) {
        val r = ByteReader(data)
        r.readU16BE() // format
        r.readU16BE() // length
        languageID = r.readU16BE()
        firstChar = r.readU16BE()
        val count = r.readU16BE()
        glyphIndex = IntArray(count)
        for (i in 0 until count) glyphIndex[i] = r.readU16BE()
    }
}

/**
 * Cmap subtable format 10 -- trimmed array (32-bit).
 */
public class CmapSubtableFormat10 : CmapSubtable() {
    override val format: Int get() = 10
    public var languageID: Int = 0
    public var firstChar: Int = 0
    public var glyphIndex: IntArray = IntArray(0)

    override fun getGlyphIndex(charCode: Int): Int =
        if (charCode >= firstChar && charCode < firstChar + glyphIndex.size)
            glyphIndex[charCode - firstChar]
        else 0

    override fun compile(): ByteArray {
        val w = ByteWriter(20 + glyphIndex.size * 2)
        w.writeU16BE(10)
        w.writeU16BE(0)   // subformat
        w.writeIntBE(20 + glyphIndex.size * 2)
        w.writeIntBE(languageID)
        w.writeIntBE(firstChar)
        w.writeIntBE(glyphIndex.size)
        for (gi in glyphIndex) w.writeU16BE(gi)
        return w.toByteArray()
    }

    override fun decompile(data: ByteArray) {
        val r = ByteReader(data)
        r.readU16BE() // format
        r.readU16BE() // subformat
        r.readIntBE() // length
        languageID = r.readIntBE()
        firstChar = r.readIntBE()
        val count = r.readIntBE()
        glyphIndex = IntArray(count)
        for (i in 0 until count) glyphIndex[i] = r.readU16BE()
    }
}

/**
 * Cmap subtable format 12 -- segmented coverage (32-bit).
 * Used for fonts with characters beyond the BMP.
 */
public class CmapSubtableFormat12 : CmapSubtable() {
    override val format: Int get() = 12
    public var languageID: Int = 0
    public val entries: MutableList<CmapSubtableSequentialEntry> = mutableListOf()

    override fun getGlyphIndex(charCode: Int): Int {
        for (e in entries) {
            if (e.contains(charCode)) return e.getGlyphIndex(charCode)
        }
        return 0
    }

    override fun compile(): ByteArray {
        val w = ByteWriter(16 + entries.size * 12)
        w.writeU16BE(12)  // format
        w.writeU16BE(0)   // subformat
        w.writeIntBE(16 + entries.size * 12)
        w.writeIntBE(languageID)
        w.writeIntBE(entries.size)
        for (e in entries) {
            w.writeIntBE(e.startCharCode)
            w.writeIntBE(e.endCharCode)
            w.writeIntBE(e.glyphIndex)
        }
        return w.toByteArray()
    }

    override fun decompile(data: ByteArray) {
        val r = ByteReader(data)
        r.readU16BE() // format
        r.readU16BE() // subformat
        r.readIntBE() // length
        languageID = r.readIntBE()
        val count = r.readIntBE()
        entries.clear()
        for (i in 0 until count) {
            val e = CmapSubtableSequentialEntry()
            e.startCharCode = r.readIntBE()
            e.endCharCode = r.readIntBE()
            e.glyphIndex = r.readIntBE()
            entries.add(e)
        }
    }
}

/**
 * Fallback for unrecognized cmap subtable formats.
 * Stores raw bytes and round-trips losslessly.
 */
public class UnknownCmapSubtable(
    override val format: Int,
    public var data: ByteArray = ByteArray(0),
) : CmapSubtable() {

    override fun getGlyphIndex(charCode: Int): Int = 0

    override fun compile(): ByteArray = data

    override fun decompile(data: ByteArray) {
        this.data = data
    }
}
