package com.kreative.bitsnpicas.ui

import com.kreative.bitsnpicas.core.BitmapFont
import com.kreative.bitsnpicas.core.BitmapGlyph
import java.awt.image.BufferedImage

/**
 * JVM (Swing/AWT) actual for [UiDriver].
 *
 * All rendering uses [BufferedImage] off-screen, which works in headless mode
 * (`-Djava.awt.headless=true`).  No display or toolkit initialisation needed.
 */
actual class UiDriver actual constructor() {

    private var font: BitmapFont? = null

    actual fun loadBitmapFont(font: BitmapFont) {
        this.font = font
    }

    actual fun renderGlyph(codepoint: Int, size: Int): ArgbBitmap {
        val f = font ?: throw IllegalStateException("No font loaded")
        val glyph = f.glyphs[codepoint]
            ?: throw IllegalArgumentException("No glyph for codepoint U+${codepoint.toString(16).uppercase().padStart(4, '0')}")
        return renderBitmapGlyph(glyph, size)
    }

    actual fun renderText(text: String, size: Int): ArgbBitmap {
        val f = font ?: throw IllegalStateException("No font loaded")
        // Render each codepoint side-by-side
        val codepoints = text.codePoints().toArray()
        if (codepoints.isEmpty()) return ArgbBitmap(0, 0, IntArray(0))

        val bitmaps = codepoints.map { cp ->
            val glyph = f.glyphs[cp]
                ?: throw IllegalArgumentException("No glyph for codepoint U+${cp.toString(16).uppercase().padStart(4, '0')}")
            glyph to renderBitmapGlyph(glyph, size)
        }

        // Combine horizontally: total width = sum of advances * size, height = max height
        val totalWidth = bitmaps.sumOf { it.second.width }
        val maxHeight = bitmaps.maxOf { it.second.height }
        val combined = IntArray(totalWidth * maxHeight)
        var xOff = 0
        for ((_, bmp) in bitmaps) {
            for (row in 0 until bmp.height) {
                for (col in 0 until bmp.width) {
                    if (row < maxHeight) {
                        combined[row * totalWidth + xOff + col] = bmp.pixels[row * bmp.width + col]
                    }
                }
            }
            xOff += bmp.width
        }
        return ArgbBitmap(totalWidth, maxHeight, combined)
    }

    actual fun dispose() {
        font = null
    }

    private fun renderBitmapGlyph(glyph: BitmapGlyph, size: Int): ArgbBitmap {
        val bitmapRows = glyph.bitmap
        if (bitmapRows.isEmpty()) return ArgbBitmap(0, 0, IntArray(0))

        val glyphW = glyph.width
        val glyphH = glyph.height
        val w = glyphW * size
        val h = glyphH * size

        if (w == 0 || h == 0) return ArgbBitmap(0, 0, IntArray(0))

        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g2 = img.createGraphics()
        try {
            // Render each pixel of the glyph bitmap as a size x size block
            for (row in 0 until glyphH) {
                val rowData = bitmapRows[row]
                for (col in 0 until rowData.size) {
                    val intensity = rowData[col] and 0xFF
                    if (intensity > 0) {
                        // Black pixel with alpha = intensity
                        val argb = (intensity shl 24) or 0x000000
                        g2.color = java.awt.Color(argb, true)
                        g2.fillRect(col * size, row * size, size, size)
                    }
                }
            }
        } finally {
            g2.dispose()
        }

        val pixels = IntArray(w * h)
        img.getRGB(0, 0, w, h, pixels, 0, w)
        return ArgbBitmap(w, h, pixels)
    }
}
