package com.kreative.bitsnpicas.core.truetype

import com.kreative.bitsnpicas.core.ByteReader
import com.kreative.bitsnpicas.core.ByteWriter

/**
 * The `COLR` table — Colour Layer table (v0).
 *
 * Maps glyph IDs to ordered layers of other glyphs with palette indices.
 * Version 1 extensions (baseGlyphList, layerList, etc.) are not yet parsed;
 * their offsets are preserved as zeros on compile.
 *
 * Spec: https://docs.microsoft.com/en-us/typography/opentype/spec/colr
 */
public class ColrTable : TrueTypeTable() {

    override val tableName: String get() = "COLR"

    public var version: Int = 0
    public var baseGlyphRecords: MutableList<BaseGlyph> = mutableListOf()
    public var layerRecords: MutableList<Layer> = mutableListOf()

    override fun compile(dependencies: Map<Int, TrueTypeTable>): ByteArray {
        val w = ByteWriter()
        var offset = if (version < 1) 14 else 34

        w.writeU16BE(version)

        // baseGlyphRecords
        w.writeU16BE(baseGlyphRecords.size)
        if (baseGlyphRecords.isNotEmpty()) {
            w.writeIntBE(offset)
            offset += baseGlyphRecords.size * 6
        } else {
            w.writeIntBE(0)
        }

        // layerRecords
        if (layerRecords.isNotEmpty()) {
            w.writeIntBE(offset)
            offset += layerRecords.size * 4
            w.writeU16BE(layerRecords.size)
        } else {
            w.writeIntBE(0)
            w.writeU16BE(0)
        }

        // Version 1 placeholder offsets
        if (version >= 1) {
            w.writeIntBE(0) // baseGlyphListOffset
            w.writeIntBE(0) // layerListOffset
            w.writeIntBE(0) // clipListOffset
            w.writeIntBE(0) // varIndexMapOffset
            w.writeIntBE(0) // itemVariationStoreOffset
        }

        // Base glyph records
        for (record in baseGlyphRecords) {
            w.writeU16BE(record.glyphID)
            w.writeU16BE(record.firstLayerIndex)
            w.writeU16BE(record.numLayers)
        }

        // Layer records
        for (record in layerRecords) {
            w.writeU16BE(record.glyphID)
            w.writeU16BE(record.paletteIndex)
        }

        return w.toByteArray()
    }

    override fun decompile(data: ByteArray, dependencies: Map<Int, TrueTypeTable>) {
        val r = ByteReader(data)
        version = r.readU16BE()
        val numBaseGlyphRecords = r.readU16BE()
        val baseGlyphRecordsOffset = r.readIntBE()
        val layerRecordsOffset = r.readIntBE()
        val numLayerRecords = r.readU16BE()

        // Skip v1 fields if present (not parsed yet)
        // version >= 1 has 5 additional int32 fields

        baseGlyphRecords = mutableListOf()
        if (baseGlyphRecordsOffset > 0) {
            r.seek(baseGlyphRecordsOffset)
            for (i in 0 until numBaseGlyphRecords) {
                val bg = BaseGlyph()
                bg.glyphID = r.readU16BE()
                bg.firstLayerIndex = r.readU16BE()
                bg.numLayers = r.readU16BE()
                baseGlyphRecords.add(bg)
            }
        }

        layerRecords = mutableListOf()
        if (layerRecordsOffset > 0) {
            r.seek(layerRecordsOffset)
            for (i in 0 until numLayerRecords) {
                val layer = Layer()
                layer.glyphID = r.readU16BE()
                layer.paletteIndex = r.readU16BE()
                layerRecords.add(layer)
            }
        }
    }

    public class BaseGlyph {
        public var glyphID: Int = 0
        public var firstLayerIndex: Int = 0
        public var numLayers: Int = 0
    }

    public class Layer {
        public var glyphID: Int = 0
        public var paletteIndex: Int = 0
    }
}
