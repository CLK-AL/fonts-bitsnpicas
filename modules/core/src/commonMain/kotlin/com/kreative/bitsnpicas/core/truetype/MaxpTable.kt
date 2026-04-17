package com.kreative.bitsnpicas.core.truetype

import com.kreative.bitsnpicas.core.ByteReader
import com.kreative.bitsnpicas.core.ByteWriter

/**
 * The `maxp` table -- maximum profile.
 *
 * Two versions: 0.5 (VERSION_CFF = 0x00005000) for CFF outlines
 * with only numGlyphs, and 1.0 (VERSION_DEFAULT = 0x00010000)
 * for TrueType outlines with full metrics.
 *
 * Spec: https://docs.microsoft.com/en-us/typography/opentype/spec/maxp
 */
public class MaxpTable : TrueTypeTable() {

    override val tableName: String get() = "maxp"

    public var version: Int = VERSION_DEFAULT
    public var numGlyphs: Int = 0
    public var maxPoints: Int = 0
    public var maxContours: Int = 0
    public var maxComponentPoints: Int = 0
    public var maxComponentContours: Int = 0
    public var maxZones: Int = 2
    public var maxTwilightPoints: Int = 0
    public var maxStorage: Int = 0
    public var maxFunctionDefs: Int = 0
    public var maxInstructionDefs: Int = 0
    public var maxStackElements: Int = 0
    public var maxSizeOfInstructions: Int = 0
    public var maxComponentElements: Int = 0
    public var maxComponentDepth: Int = 0

    override fun compile(dependencies: Map<Int, TrueTypeTable>): ByteArray {
        val w = ByteWriter(32)
        w.writeIntBE(version)
        w.writeU16BE(numGlyphs)
        if (version < VERSION_DEFAULT) return w.toByteArray()
        w.writeU16BE(maxPoints)
        w.writeU16BE(maxContours)
        w.writeU16BE(maxComponentPoints)
        w.writeU16BE(maxComponentContours)
        w.writeU16BE(maxZones)
        w.writeU16BE(maxTwilightPoints)
        w.writeU16BE(maxStorage)
        w.writeU16BE(maxFunctionDefs)
        w.writeU16BE(maxInstructionDefs)
        w.writeU16BE(maxStackElements)
        w.writeU16BE(maxSizeOfInstructions)
        w.writeU16BE(maxComponentElements)
        w.writeU16BE(maxComponentDepth)
        return w.toByteArray()
    }

    override fun decompile(data: ByteArray, dependencies: Map<Int, TrueTypeTable>) {
        val r = ByteReader(data)
        version = r.readIntBE()
        numGlyphs = r.readU16BE()
        if (version < VERSION_DEFAULT) return
        maxPoints = r.readU16BE()
        maxContours = r.readU16BE()
        maxComponentPoints = r.readU16BE()
        maxComponentContours = r.readU16BE()
        maxZones = r.readU16BE()
        maxTwilightPoints = r.readU16BE()
        maxStorage = r.readU16BE()
        maxFunctionDefs = r.readU16BE()
        maxInstructionDefs = r.readU16BE()
        maxStackElements = r.readU16BE()
        maxSizeOfInstructions = r.readU16BE()
        maxComponentElements = r.readU16BE()
        maxComponentDepth = r.readU16BE()
    }

    public companion object {
        public const val VERSION_DEFAULT: Int = 0x00010000
        public const val VERSION_CFF: Int = 0x00005000
    }
}
