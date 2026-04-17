package com.kreative.bitsnpicas.core.truetype

import com.kreative.bitsnpicas.core.ByteReader
import com.kreative.bitsnpicas.core.ByteWriter

/**
 * The `hhea` table -- horizontal header.
 *
 * Fixed 36-byte table containing global horizontal layout metrics.
 *
 * Spec: https://docs.microsoft.com/en-us/typography/opentype/spec/hhea
 */
public class HheaTable : TrueTypeTable() {

    override val tableName: String get() = "hhea"

    public var version: Int = VERSION_DEFAULT
    public var ascent: Int = 0
    public var descent: Int = 0
    public var lineGap: Int = 0
    public var advanceWidthMax: Int = 0
    public var minLeftSideBearing: Int = 0
    public var minRightSideBearing: Int = 0
    public var xMaxExtent: Int = 0
    public var caretSlopeRise: Int = 1
    public var caretSlopeRun: Int = 0
    public var caretOffset: Int = 0
    public var reserved1: Int = 0
    public var reserved2: Int = 0
    public var reserved3: Int = 0
    public var reserved4: Int = 0
    public var metricDataFormat: Int = 0
    public var numLongHorMetrics: Int = 0

    override fun compile(dependencies: Map<Int, TrueTypeTable>): ByteArray {
        val w = ByteWriter(36)
        w.writeIntBE(version)
        w.writeI16BE(ascent)
        w.writeI16BE(descent)
        w.writeI16BE(lineGap)
        w.writeU16BE(advanceWidthMax)
        w.writeI16BE(minLeftSideBearing)
        w.writeI16BE(minRightSideBearing)
        w.writeI16BE(xMaxExtent)
        w.writeI16BE(caretSlopeRise)
        w.writeI16BE(caretSlopeRun)
        w.writeI16BE(caretOffset)
        w.writeI16BE(reserved1)
        w.writeI16BE(reserved2)
        w.writeI16BE(reserved3)
        w.writeI16BE(reserved4)
        w.writeI16BE(metricDataFormat)
        w.writeU16BE(numLongHorMetrics)
        return w.toByteArray()
    }

    override fun decompile(data: ByteArray, dependencies: Map<Int, TrueTypeTable>) {
        val r = ByteReader(data)
        version = r.readIntBE()
        ascent = r.readI16BE()
        descent = r.readI16BE()
        lineGap = r.readI16BE()
        advanceWidthMax = r.readU16BE()
        minLeftSideBearing = r.readI16BE()
        minRightSideBearing = r.readI16BE()
        xMaxExtent = r.readI16BE()
        caretSlopeRise = r.readI16BE()
        caretSlopeRun = r.readI16BE()
        caretOffset = r.readI16BE()
        reserved1 = r.readI16BE()
        reserved2 = r.readI16BE()
        reserved3 = r.readI16BE()
        reserved4 = r.readI16BE()
        metricDataFormat = r.readI16BE()
        numLongHorMetrics = r.readU16BE()
    }

    public companion object {
        public const val VERSION_DEFAULT: Int = 0x00010000
    }
}
