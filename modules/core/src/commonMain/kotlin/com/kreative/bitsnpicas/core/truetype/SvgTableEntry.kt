package com.kreative.bitsnpicas.core.truetype

/**
 * A single entry in the [SvgTable] SVG document index.
 *
 * Each entry maps a contiguous range of glyph IDs to an SVG document
 * stored as a raw byte array (may be gzip-compressed per spec, though
 * browser support varies). No decompression is performed in commonMain;
 * the document bytes are stored as-is.
 */
public class SvgTableEntry : Comparable<SvgTableEntry> {
    public var startGlyphID: Int = 0
    public var endGlyphID: Int = 0
    public var svgDocument: ByteArray = ByteArray(0)

    /** Check whether the SVG data appears to be gzip-compressed (magic bytes 1F 8B). */
    public fun isCompressed(): Boolean =
        svgDocument.size >= 18 &&
        (svgDocument[0].toInt() and 0xFF) == 0x1F &&
        (svgDocument[1].toInt() and 0xFF) == 0x8B

    override fun compareTo(other: SvgTableEntry): Int =
        this.startGlyphID - other.startGlyphID
}
