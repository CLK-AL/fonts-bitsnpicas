package com.kreative.bitsnpicas.ui

import org.apache.fontbox.ttf.TTFParser
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.GeneralPath
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream

/**
 * JVM-only helper that renders a TTF glyph outline to an [ArgbBitmap]
 * using Apache FontBox for parsing and Java2D for rasterisation.
 *
 * FontBox is used (rather than `java.awt.Font`) because it gives access
 * to the raw glyph outlines without requiring OS font registration, and
 * it handles hinting via its own scaler tables.
 */
public object FontBoxRenderer {

    /**
     * Load a TTF from raw bytes, look up the glyph for [codepoint],
     * render it at the requested [size] (in pixels-per-em), and return
     * the result as an [ArgbBitmap].
     *
     * @param ttfBytes  complete TTF file bytes.
     * @param codepoint the Unicode code point to render.
     * @param size      pixel-per-em size (determines output dimensions).
     * @return an [ArgbBitmap] with the rendered glyph (may be 0x0 if the
     *         glyph has no outline).
     */
    public fun renderTtfGlyph(ttfBytes: ByteArray, codepoint: Int, size: Int): ArgbBitmap {
        val parser = TTFParser(true)
        val ttf = parser.parseEmbedded(ByteArrayInputStream(ttfBytes))

        val cmap = ttf.cmap ?: error("TTF has no cmap table")
        val cmapSub = cmap.getSubtable(3, 1) // Windows / Unicode BMP
            ?: cmap.getSubtable(0, 3)         // Unicode / 2.0
            ?: cmap.getSubtable(0, 1)         // Unicode / 1.1
            ?: error("No usable cmap subtable found")

        val gid = cmapSub.getGlyphId(codepoint)
        require(gid != 0) { "Codepoint U+${codepoint.toString(16).uppercase().padStart(4, '0')} not mapped in cmap" }

        val glyf = ttf.glyph
        val glyphData = glyf.getGlyph(gid) ?: return ArgbBitmap(0, 0, IntArray(0))

        val path: GeneralPath = glyphData.path
            ?: return ArgbBitmap(0, 0, IntArray(0))

        val unitsPerEm = ttf.unitsPerEm.toFloat()
        if (unitsPerEm == 0f) return ArgbBitmap(0, 0, IntArray(0))

        val scale = size.toFloat() / unitsPerEm

        // Transform: scale + flip Y (TTF Y-up -> bitmap Y-down) + translate.
        val tx = AffineTransform()
        tx.scale(scale.toDouble(), (-scale).toDouble())

        val transformedPath = GeneralPath(path)
        transformedPath.transform(tx)

        val bounds = transformedPath.bounds2D
        if (bounds.width < 1 || bounds.height < 1) return ArgbBitmap(0, 0, IntArray(0))

        val w = kotlin.math.ceil(bounds.width).toInt() + 2
        val h = kotlin.math.ceil(bounds.height).toInt() + 2

        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g2 = img.createGraphics()
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.translate(-bounds.x + 1.0, -bounds.y + 1.0)
            g2.color = Color.BLACK
            g2.fill(transformedPath)
        } finally {
            g2.dispose()
        }

        ttf.close()

        val pixels = IntArray(w * h)
        img.getRGB(0, 0, w, h, pixels, 0, w)
        return ArgbBitmap(w, h, pixels)
    }
}
