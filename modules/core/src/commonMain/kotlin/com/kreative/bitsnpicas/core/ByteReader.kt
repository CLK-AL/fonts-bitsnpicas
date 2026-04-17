package com.kreative.bitsnpicas.core

/**
 * Minimal sequential byte reader over a ByteArray.
 * Tracks position; throws [ParseException] on underflow.
 *
 * Extracted from PsfImporter so it can be shared across
 * all bitmap font importers in commonMain.
 */
public class ByteReader(private val data: ByteArray) {
    public var pos: Int = 0
        private set

    public val remaining: Int get() = data.size - pos

    public val size: Int get() = data.size

    public fun readU8(): Int {
        if (pos >= data.size) throw ParseException("unexpected end of data at offset $pos")
        return data[pos++].toInt() and 0xFF
    }

    /** Read a signed byte (-128..127). */
    public fun readI8(): Int {
        if (pos >= data.size) throw ParseException("unexpected end of data at offset $pos")
        return data[pos++].toInt()
    }

    /** Read 16-bit unsigned, big-endian (matches DataInputStream.readUnsignedShort). */
    public fun readU16BE(): Int {
        val hi = readU8()
        val lo = readU8()
        return (hi shl 8) or lo
    }

    /** Read 16-bit signed, big-endian (matches DataInputStream.readShort). */
    public fun readI16BE(): Int {
        val v = readU16BE()
        return if (v >= 0x8000) v - 0x10000 else v
    }

    /** Read 16-bit unsigned, little-endian. */
    public fun readU16LE(): Int {
        val lo = readU8()
        val hi = readU8()
        return (hi shl 8) or lo
    }

    /** Read 32-bit signed, big-endian (matches DataInputStream.readInt). */
    public fun readIntBE(): Int {
        val b3 = readU8()
        val b2 = readU8()
        val b1 = readU8()
        val b0 = readU8()
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }

    /** Read 64-bit signed, big-endian (matches DataInputStream.readLong). */
    public fun readLongBE(): Long {
        val hi = readIntBE().toLong() and 0xFFFFFFFFL
        val lo = readIntBE().toLong() and 0xFFFFFFFFL
        return (hi shl 32) or lo
    }

    /** Read 32-bit signed, little-endian. */
    public fun readIntLE(): Int {
        val b0 = readU8()
        val b1 = readU8()
        val b2 = readU8()
        val b3 = readU8()
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }

    /** Read raw byte at the given absolute index without advancing the position. */
    public fun byteAt(index: Int): Int {
        if (index < 0 || index >= data.size) throw ParseException("byte index $index out of bounds (size=${data.size})")
        return data[index].toInt() and 0xFF
    }

    public fun readBytes(count: Int): ByteArray {
        if (pos + count > data.size)
            throw ParseException("unexpected end of data: need $count bytes at offset $pos, have ${data.size - pos}")
        val result = data.copyOfRange(pos, pos + count)
        pos += count
        return result
    }

    public fun skip(count: Int) {
        if (pos + count > data.size)
            throw ParseException("unexpected end of data: cannot skip $count bytes at offset $pos")
        pos += count
    }

    /** Seek to an absolute position in the data. */
    public fun seek(offset: Int) {
        if (offset < 0 || offset > data.size)
            throw ParseException("seek offset $offset out of bounds (size=${data.size})")
        pos = offset
    }
}

/**
 * Common parse exception for font importers.
 */
public open class ParseException(message: String) : Exception(message)
