package com.kreative.bitsnpicas.ui

import com.kreative.bitsnpicas.core.BitmapFont

/**
 * Platform-specific rendering driver for bitmap fonts.
 * Each platform (Swing, Compose Desktop, Compose Web) provides an actual implementation.
 */
expect class UiDriver() {
    fun loadBitmapFont(font: BitmapFont)
    fun renderGlyph(codepoint: Int, size: Int): ArgbBitmap
    fun renderText(text: String, size: Int): ArgbBitmap
    fun dispose()
}

/**
 * A simple ARGB pixel buffer for cross-platform glyph rendering comparison.
 */
data class ArgbBitmap(val width: Int, val height: Int, val pixels: IntArray) {

    /** SHA-256 hex digest of the raw pixel data (big-endian int encoding). */
    fun sha256(): String = sha256Hex(pixelBytes())

    private fun pixelBytes(): ByteArray {
        val buf = ByteArray(pixels.size * 4)
        var off = 0
        for (p in pixels) {
            buf[off++] = (p shr 24).toByte()
            buf[off++] = (p shr 16).toByte()
            buf[off++] = (p shr 8).toByte()
            buf[off++] = p.toByte()
        }
        return buf
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArgbBitmap) return false
        return width == other.width && height == other.height && pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}

/** Platform-specific SHA-256 hex digest. */
expect fun sha256Hex(data: ByteArray): String
