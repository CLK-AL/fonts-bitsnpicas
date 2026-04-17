package com.kreative.bitsnpicas.core.truetype

import com.kreative.bitsnpicas.core.ByteReader
import com.kreative.bitsnpicas.core.ByteWriter

/**
 * The `post` table -- PostScript name mapping.
 *
 * Supports formats 1, 2, 2.5, 3, and 4 as defined
 * in the TrueType/OpenType specification.
 */
public class PostTable : TrueTypeTable() {

    override val tableName: String get() = "post"

    public var format: Int = FORMAT_2
    public var italicAngle: Int = ITALIC_ANGLE_UPRIGHT
    public var underlinePosition: Int = 0
    public var underlineThickness: Int = 0
    public var fixedPitch: Int = FIXED_PITCH_FALSE
    public var minMemType42: Int = MEM_UNKNOWN
    public var maxMemType42: Int = MEM_UNKNOWN
    public var minMemType1: Int = MEM_UNKNOWN
    public var maxMemType1: Int = MEM_UNKNOWN
    public val entries: MutableList<PostTableEntry> = mutableListOf()

    override fun compile(dependencies: Map<Int, TrueTypeTable>): ByteArray {
        val w = ByteWriter(256)
        w.writeIntBE(format)
        w.writeIntBE(italicAngle)
        w.writeI16BE(underlinePosition)
        w.writeI16BE(underlineThickness)
        w.writeIntBE(fixedPitch)
        w.writeIntBE(minMemType42)
        w.writeIntBE(maxMemType42)
        w.writeIntBE(minMemType1)
        w.writeIntBE(maxMemType1)

        when (format) {
            FORMAT_1 -> { /* No additional content */ }
            FORMAT_2 -> {
                w.writeU16BE(entries.size)
                var stringIndex = 258
                for (e in entries) {
                    if (e.isInteger()) {
                        w.writeU16BE(e.intValue())
                    } else if (e.isString()) {
                        w.writeU16BE(stringIndex++)
                    }
                }
                for (e in entries) {
                    if (e.isString()) {
                        val data = e.stringValue().encodeToByteArray() // ASCII
                        w.writeU8(data.size)
                        w.writeBytes(data)
                        stringIndex--
                    }
                }
                check(stringIndex == 258) {
                    "Assertion failed: number of names written <> number of names assigned."
                }
            }
            FORMAT_2_5 -> {
                w.writeU16BE(entries.size)
                for (i in entries.indices) {
                    val e = entries[i]
                    check(e.isInteger()) { "Invalid entry in Format 2.5 'post' table." }
                    // Write signed byte: offset from index
                    w.writeU8((e.intValue() - i) and 0xFF)
                }
            }
            FORMAT_3 -> { /* No additional content */ }
            FORMAT_4 -> {
                for (e in entries) {
                    check(e.isInteger()) { "Invalid entry in Format 4 'post' table." }
                    w.writeU16BE(e.intValue())
                }
            }
            else -> error("Invalid format for 'post' table.")
        }

        return w.toByteArray()
    }

    override fun decompile(data: ByteArray, dependencies: Map<Int, TrueTypeTable>) {
        val r = ByteReader(data)
        format = r.readIntBE()
        italicAngle = r.readIntBE()
        underlinePosition = r.readI16BE()
        underlineThickness = r.readI16BE()
        fixedPitch = r.readIntBE()
        minMemType42 = r.readIntBE()
        maxMemType42 = r.readIntBE()
        minMemType1 = r.readIntBE()
        maxMemType1 = r.readIntBE()

        entries.clear()
        when (format) {
            FORMAT_1 -> { /* No additional content */ }
            FORMAT_2 -> {
                val count = r.readU16BE()
                val intValues = IntArray(count) { r.readU16BE() }
                val maxValue = intValues.maxOrNull() ?: 0
                val stringValues = arrayOfNulls<String>(maxValue + 1)
                for (i in 258 until stringValues.size) {
                    val len = r.readU8()
                    val d = r.readBytes(len)
                    stringValues[i] = d.decodeToString() // ASCII
                }
                for (i in 0 until count) {
                    val iv = intValues[i]
                    val sv = stringValues.getOrNull(iv)
                    if (sv != null) {
                        entries.add(PostTableEntry(sv))
                    } else {
                        entries.add(PostTableEntry(iv))
                    }
                }
            }
            FORMAT_2_5 -> {
                val count = r.readU16BE()
                for (i in 0 until count) {
                    val iv = i + r.readI8()
                    entries.add(PostTableEntry(iv))
                }
            }
            FORMAT_3 -> { /* No additional content */ }
            FORMAT_4 -> {
                val n = (data.size - 32) / 2
                for (i in 0 until n) {
                    var iv = r.readU16BE()
                    if (iv == 0xFFFF) iv = -1
                    entries.add(PostTableEntry(iv))
                }
            }
            else -> error("Invalid format for 'post' table.")
        }
    }

    public companion object {
        public const val FORMAT_1: Int = 0x00010000
        public const val FORMAT_2: Int = 0x00020000
        public const val FORMAT_2_5: Int = 0x00028000
        public const val FORMAT_3: Int = 0x00030000
        public const val FORMAT_4: Int = 0x00040000
        public const val ITALIC_ANGLE_UPRIGHT: Int = 0
        public const val FIXED_PITCH_FALSE: Int = 0
        public const val FIXED_PITCH_TRUE: Int = 1
        public const val MEM_UNKNOWN: Int = 0
    }
}
