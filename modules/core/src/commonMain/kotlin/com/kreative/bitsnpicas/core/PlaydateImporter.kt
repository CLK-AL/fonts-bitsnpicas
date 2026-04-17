package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.PlaydateMetadataParser.PlaydateMetadata

/**
 * Pure-Kotlin multiplatform importer for the Playdate bitmap font format.
 *
 * Takes pre-decoded ARGB pixel data (platform-specific PNG decoding happens
 * elsewhere) and the parsed `.fnt` metadata, and produces a [BitmapFont] by
 * slicing the pixel matrix according to metadata glyph widths.
 *
 * Ported from the pixel-processing portion of the frozen Java
 * `PlaydateBitmapFontImporter`.
 */
public object PlaydateImporter {

    /**
     * Import a Playdate bitmap font from pre-decoded pixel data and metadata.
     *
     * @param metadata  parsed `.fnt` metadata from [PlaydateMetadataParser.parse].
     * @param pixels    the sprite-sheet image as row-major ARGB pixel data.
     *                  `pixels[y]` is a row of width pixels, each as a packed
     *                  ARGB int (0xAARRGGBB). The array length is the image height.
     * @param name      fallback font name if metadata has none.
     * @return a [BitmapFont] with glyphs sliced from the sprite sheet.
     * @throws IllegalArgumentException if cell dimensions are invalid or the
     *         image is too small for even one cell.
     */
    public fun import(
        metadata: PlaydateMetadata,
        pixels: Array<IntArray>,
        name: String = "Untitled",
    ): BitmapFont {
        val cw = metadata.cellWidth
            ?: throw IllegalArgumentException("cellWidth is required")
        val ch = metadata.cellHeight
            ?: throw IllegalArgumentException("cellHeight is required")
        require(cw > 0 && ch > 0) { "Invalid cell size: $cw x $ch" }

        val imgHeight = pixels.size
        require(imgHeight > 0) { "Image has no pixel rows" }
        val imgWidth = pixels[0].size
        require(imgWidth > 0) { "Image has no pixel columns" }

        val cols = imgWidth / cw
        val rows = imgHeight / ch
        require(cols > 0 && rows > 0) { "Image too small for cell size: $cw x $ch" }

        val fontName = metadata.name ?: name
        val tracking = metadata.tracking
        val ascent = when {
            metadata.baseline == null -> ch
            metadata.baseline > 0 -> metadata.baseline
            else -> ch + metadata.baseline
        }

        val glyphs = mutableMapOf<Int, BitmapGlyph>()
        var row = 0
        var col = 0
        for ((codePoint, charWidth) in metadata.glyphWidths) {
            // Extract the cell pixels.
            val glyphData = List(ch) { y ->
                IntArray(cw) { x ->
                    val px = pixels[row * ch + y][col * cw + x]
                    pixelToGray(px)
                }
            }

            val glyph = BitmapGlyph(
                bitmap = glyphData,
                x = 0,
                advance = charWidth + tracking,
                y = ascent,
            ).contract()

            glyphs[codePoint] = glyph

            col++
            if (col >= cols) {
                col = 0
                row++
                if (row >= rows) row = 0
            }
        }

        return BitmapFont(
            glyphs = glyphs,
            emAscent = ascent,
            emDescent = ch - ascent,
            lineAscent = ascent,
            lineDescent = ch - ascent,
            xHeight = metadata.xHeight ?: 0,
            capHeight = metadata.capHeight ?: 0,
            lineGap = 0,
            newGlyphWidth = cw,
            name = fontName,
        )
    }

    /**
     * Convert a packed ARGB pixel to a grayscale intensity value (0..255).
     *
     * Matches the frozen Java logic:
     * - Fully transparent pixels (alpha = 0) -> 0 (empty).
     * - Opaque/semi-opaque pixels: convert to grayscale via
     *   `30*R + 59*G + 11*B`. If the result < 12750 (i.e. dark), the
     *   pixel is "on" (foreground, value 255); otherwise "off" but marked
     *   as a dim pixel (value 51, used for glyph extent detection before
     *   contraction).
     *
     * The frozen Java uses `byte` values -1 (0xFF=255) for "on" and
     * 51 for "dim". Here we use unsigned int equivalents.
     */
    internal fun pixelToGray(argb: Int): Int {
        // Check alpha (top byte). Fully transparent -> empty.
        if (argb >= 0) return 0 // alpha bit not set (alpha < 128)
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val luminance = 30 * r + 59 * g + 11 * b
        return if (luminance < 12750) 255 else 51
    }
}
