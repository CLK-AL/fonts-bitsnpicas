package com.kreative.bitsnpicas.core.truetype

import com.kreative.bitsnpicas.core.ByteReader
import com.kreative.bitsnpicas.core.ByteWriter

/**
 * The `GPOS` table — Glyph Positioning table (envelope only).
 *
 * Parses the table header (version, script list offset, feature list offset,
 * lookup list offset) and stores the raw table data for lossless round-trip.
 * Individual lookup types are NOT parsed; they are preserved as raw bytes.
 *
 * Full lookup parsing (pair adjustment, mark attachment, etc.) is deferred
 * to a follow-up.
 *
 * Spec: https://docs.microsoft.com/en-us/typography/opentype/spec/gpos
 */
public class GposTable : TrueTypeTable() {

    override val tableName: String get() = "GPOS"

    /** Major.minor version as a fixed-point u32 (e.g. 0x00010000 for 1.0). */
    public var version: Int = 0x00010000

    /** Offset to ScriptList from beginning of table. */
    public var scriptListOffset: Int = 0

    /** Offset to FeatureList from beginning of table. */
    public var featureListOffset: Int = 0

    /** Offset to LookupList from beginning of table. */
    public var lookupListOffset: Int = 0

    /**
     * Offset to FeatureVariations table (v1.1+). 0 if not present.
     * Only read/written when version >= 0x00010001.
     */
    public var featureVariationsOffset: Int = 0

    /**
     * Raw table data for lossless round-trip.
     * Compile returns this directly; decompile stores it and extracts header fields.
     */
    public var rawData: ByteArray = ByteArray(0)

    override fun compile(dependencies: Map<Int, TrueTypeTable>): ByteArray = rawData

    override fun decompile(data: ByteArray, dependencies: Map<Int, TrueTypeTable>) {
        rawData = data
        if (data.size < 10) return

        val r = ByteReader(data)
        version = r.readIntBE()
        scriptListOffset = r.readU16BE()
        featureListOffset = r.readU16BE()
        lookupListOffset = r.readU16BE()

        featureVariationsOffset = if (version >= 0x00010001 && data.size >= 14) {
            r.readIntBE()
        } else {
            0
        }
    }
}
