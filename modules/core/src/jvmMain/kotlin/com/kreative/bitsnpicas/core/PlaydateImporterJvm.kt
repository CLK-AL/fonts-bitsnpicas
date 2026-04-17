package com.kreative.bitsnpicas.core

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * JVM-only helper that decodes a PNG sprite sheet and delegates to
 * [PlaydateImporter] to produce a [BitmapFont].
 *
 * This is the platform-specific bridge: it uses [javax.imageio.ImageIO]
 * to decode PNG bytes into an ARGB pixel matrix, then passes the matrix
 * to the pure-Kotlin [PlaydateImporter.import] function.
 */
public object PlaydateImporterJvm {

    /**
     * Import a Playdate bitmap font from raw file contents.
     *
     * @param fntText   the text content of the `.fnt` metadata file.
     * @param pngBytes  the raw bytes of the `-table-W-H.png` sprite sheet.
     * @param name      fallback font name if the `.fnt` has no `name` property.
     * @return a [BitmapFont] with glyphs sliced from the sprite sheet.
     */
    public fun importFromFiles(
        fntText: String,
        pngBytes: ByteArray,
        name: String = "Untitled",
    ): BitmapFont {
        val metadata = PlaydateMetadataParser.parse(fntText)
        val image = ImageIO.read(ByteArrayInputStream(pngBytes))
            ?: throw IllegalArgumentException("Could not decode PNG image")

        val width = image.width
        val height = image.height
        val pixels = Array(height) { y ->
            IntArray(width) { x -> image.getRGB(x, y) }
        }

        return PlaydateImporter.import(metadata, pixels, name)
    }
}
