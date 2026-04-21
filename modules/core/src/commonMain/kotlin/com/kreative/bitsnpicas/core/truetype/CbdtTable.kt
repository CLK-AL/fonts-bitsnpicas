package com.kreative.bitsnpicas.core.truetype

/**
 * The `CBDT` table — Colour Bitmap Data table.
 *
 * Stores PNG image data (and other bitmap formats) per glyph.
 * The companion [CblcTable] provides the index mapping glyph IDs
 * to offsets within this table.
 *
 * This is a simplified port that stores the entire table as raw bytes
 * for lossless round-trip. Full entry format dispatch (formats 17/18/19
 * for PNG-bearing entries, formats 1-9 for monochrome/greyscale) is
 * deferred to a follow-up that also parses the CBLC index subtables.
 *
 * The raw data includes the 4-byte version header followed by all
 * bitmap data entries at their original offsets.
 *
 * Spec: https://docs.microsoft.com/en-us/typography/opentype/spec/cbdt
 */
public class CbdtTable : TrueTypeTable() {

    override val tableName: String get() = "CBDT"

    /**
     * Raw table data (version header + all bitmap data).
     * Stored as-is for lossless round-trip.
     */
    public var data: ByteArray = ByteArray(0)

    override fun compile(dependencies: Map<Int, TrueTypeTable>): ByteArray = data

    override fun decompile(data: ByteArray, dependencies: Map<Int, TrueTypeTable>) {
        this.data = data
    }
}
