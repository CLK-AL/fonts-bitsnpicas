package com.kreative.bitsnpicas.core

/**
 * Minimal sequential byte writer that builds a [ByteArray].
 * Counterpart to [ByteReader]; used by PSF and FNT exporters
 * in commonMain so they can produce binary formats without
 * `java.io.DataOutputStream`.
 *
 * All multi-byte helpers come in both big-endian (BE) and
 * little-endian (LE) variants; callers pick the one their
 * format requires.
 */
public class ByteWriter(initialCapacity: Int = 256) {
    private var buf = ByteArray(initialCapacity)
    private var pos = 0

    /** Number of bytes written so far. */
    public val size: Int get() = pos

    /** Write a single unsigned byte (0..255). */
    public fun writeU8(value: Int) {
        ensureCapacity(1)
        buf[pos++] = (value and 0xFF).toByte()
    }

    /** Write 16 bits big-endian. */
    public fun writeU16BE(value: Int) {
        ensureCapacity(2)
        buf[pos++] = ((value shr 8) and 0xFF).toByte()
        buf[pos++] = (value and 0xFF).toByte()
    }

    /** Write 16 bits little-endian. */
    public fun writeU16LE(value: Int) {
        ensureCapacity(2)
        buf[pos++] = (value and 0xFF).toByte()
        buf[pos++] = ((value shr 8) and 0xFF).toByte()
    }

    /** Write 32 bits big-endian. */
    public fun writeIntBE(value: Int) {
        ensureCapacity(4)
        buf[pos++] = ((value shr 24) and 0xFF).toByte()
        buf[pos++] = ((value shr 16) and 0xFF).toByte()
        buf[pos++] = ((value shr 8) and 0xFF).toByte()
        buf[pos++] = (value and 0xFF).toByte()
    }

    /** Write 32 bits little-endian. */
    public fun writeIntLE(value: Int) {
        ensureCapacity(4)
        buf[pos++] = (value and 0xFF).toByte()
        buf[pos++] = ((value shr 8) and 0xFF).toByte()
        buf[pos++] = ((value shr 16) and 0xFF).toByte()
        buf[pos++] = ((value shr 24) and 0xFF).toByte()
    }

    /** Write a raw byte array. */
    public fun writeBytes(data: ByteArray) {
        ensureCapacity(data.size)
        data.copyInto(buf, pos)
        pos += data.size
    }

    /** Write [count] zero bytes. */
    public fun writeZeros(count: Int) {
        ensureCapacity(count)
        for (i in 0 until count) {
            buf[pos++] = 0
        }
    }

    /** Return the written bytes as a new [ByteArray]. */
    public fun toByteArray(): ByteArray = buf.copyOfRange(0, pos)

    private fun ensureCapacity(needed: Int) {
        val required = pos + needed
        if (required <= buf.size) return
        var newCap = buf.size * 2
        if (newCap < required) newCap = required
        val newBuf = ByteArray(newCap)
        buf.copyInto(newBuf, 0, 0, pos)
        buf = newBuf
    }
}
