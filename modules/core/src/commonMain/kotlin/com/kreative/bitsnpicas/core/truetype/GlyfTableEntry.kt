package com.kreative.bitsnpicas.core.truetype

import com.kreative.bitsnpicas.core.ByteReader
import com.kreative.bitsnpicas.core.ByteWriter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Parsed representation of a single glyph outline from the `glyf` table.
 *
 * Supports both simple glyphs (contours + coordinates) and compound glyphs
 * (component references with transforms).
 *
 * Use [compile] to produce the raw byte data for one glyph entry,
 * and [decompile] to parse raw byte data back into structured fields.
 *
 * Spec: https://docs.microsoft.com/en-us/typography/opentype/spec/glyf
 */
public class GlyfTableEntry {

    public var numberOfContours: Int = 0
    public var xMin: Int = 0
    public var yMin: Int = 0
    public var xMax: Int = 0
    public var yMax: Int = 0

    // Simple glyph fields
    public var endPointsOfContours: IntArray = IntArray(0)
    public var instructions: IntArray = IntArray(0)
    public var flags: IntArray = IntArray(0)
    public var xCoordinates: IntArray = IntArray(0)
    public var yCoordinates: IntArray = IntArray(0)

    // Compound glyph fields
    public var numberOfComponents: Int = 0
    public var componentFlags: IntArray = IntArray(0)
    public var componentGlyphIndex: IntArray = IntArray(0)
    public var componentArgument1: IntArray = IntArray(0)
    public var componentArgument2: IntArray = IntArray(0)
    public var componentTransformA: DoubleArray = DoubleArray(0)
    public var componentTransformB: DoubleArray = DoubleArray(0)
    public var componentTransformC: DoubleArray = DoubleArray(0)
    public var componentTransformD: DoubleArray = DoubleArray(0)

    public fun compile(): ByteArray {
        val w = ByteWriter(256)
        w.writeI16BE(numberOfContours)
        w.writeI16BE(xMin)
        w.writeI16BE(yMin)
        w.writeI16BE(xMax)
        w.writeI16BE(yMax)

        if (numberOfContours >= 0) {
            // Simple glyph
            var lastPointIndex = 0
            for (i in 0 until numberOfContours) {
                lastPointIndex = endPointsOfContours[i]
                w.writeU16BE(lastPointIndex)
            }
            w.writeU16BE(instructions.size)
            for (inst in instructions) {
                w.writeU8(inst)
            }
            // Write flags with repeat compression
            var i = 0
            while (i <= lastPointIndex) {
                w.writeU8(flags[i])
                if ((flags[i] and FLAG_REPEAT) != 0) {
                    var repeat = 1
                    while (i + repeat <= lastPointIndex && flags[i + repeat] == (flags[i] and FLAG_REPEAT.inv())) {
                        repeat++
                    }
                    w.writeU8(repeat - 1)
                    i += repeat
                } else {
                    i++
                }
            }
            // Write X coordinates
            var lastXCoordinate = 0
            for (j in 0..lastPointIndex) {
                if ((flags[j] and FLAG_X_SHORT_VECTOR) != 0) {
                    w.writeU8(abs(xCoordinates[j] - lastXCoordinate))
                    lastXCoordinate = xCoordinates[j]
                } else if ((flags[j] and FLAG_THIS_X_IS_SAME) != 0) {
                    lastXCoordinate = xCoordinates[j]
                } else {
                    w.writeI16BE(xCoordinates[j] - lastXCoordinate)
                    lastXCoordinate = xCoordinates[j]
                }
            }
            // Write Y coordinates
            var lastYCoordinate = 0
            for (j in 0..lastPointIndex) {
                if ((flags[j] and FLAG_Y_SHORT_VECTOR) != 0) {
                    w.writeU8(abs(yCoordinates[j] - lastYCoordinate))
                    lastYCoordinate = yCoordinates[j]
                } else if ((flags[j] and FLAG_THIS_Y_IS_SAME) != 0) {
                    lastYCoordinate = yCoordinates[j]
                } else {
                    w.writeI16BE(yCoordinates[j] - lastYCoordinate)
                    lastYCoordinate = yCoordinates[j]
                }
            }
        } else if (numberOfContours == -1) {
            // Compound glyph
            for (i in 0 until numberOfComponents) {
                val cFlags = if (i < numberOfComponents - 1) {
                    componentFlags[i] or COMPONENT_FLAG_MORE_COMPONENTS
                } else {
                    componentFlags[i] and COMPONENT_FLAG_MORE_COMPONENTS.inv()
                }
                val words = (cFlags and COMPONENT_FLAG_ARG_1_AND_2_ARE_WORDS) != 0
                val scale = (cFlags and COMPONENT_FLAG_WE_HAVE_A_SCALE) != 0
                val xyScale = (cFlags and COMPONENT_FLAG_WE_HAVE_AN_X_AND_Y_SCALE) != 0
                val twoByTwo = (cFlags and COMPONENT_FLAG_WE_HAVE_A_TWO_BY_TWO) != 0
                w.writeU16BE(cFlags)
                w.writeU16BE(componentGlyphIndex[i])
                if (words) {
                    w.writeI16BE(componentArgument1[i])
                    w.writeI16BE(componentArgument2[i])
                } else {
                    w.writeU8(componentArgument1[i] and 0xFF)
                    w.writeU8(componentArgument2[i] and 0xFF)
                }
                if (twoByTwo) {
                    w.writeI16BE(f2dot14Encode(componentTransformA[i]))
                    w.writeI16BE(f2dot14Encode(componentTransformB[i]))
                    w.writeI16BE(f2dot14Encode(componentTransformC[i]))
                    w.writeI16BE(f2dot14Encode(componentTransformD[i]))
                } else if (xyScale) {
                    w.writeI16BE(f2dot14Encode(componentTransformA[i]))
                    w.writeI16BE(f2dot14Encode(componentTransformD[i]))
                } else if (scale) {
                    w.writeI16BE(f2dot14Encode(componentTransformA[i]))
                }
            }
        } else {
            error("Unknown glyph format: numberOfContours=$numberOfContours")
        }
        return w.toByteArray()
    }

    public fun decompile(data: ByteArray) {
        val r = ByteReader(data)
        numberOfContours = r.readI16BE()
        xMin = r.readI16BE()
        yMin = r.readI16BE()
        xMax = r.readI16BE()
        yMax = r.readI16BE()

        if (numberOfContours >= 0) {
            // Simple glyph
            endPointsOfContours = IntArray(numberOfContours)
            var lastPointIndex = 0
            for (i in 0 until numberOfContours) {
                lastPointIndex = r.readU16BE()
                endPointsOfContours[i] = lastPointIndex
            }
            instructions = IntArray(r.readU16BE())
            for (i in instructions.indices) {
                instructions[i] = r.readU8()
            }
            flags = IntArray(lastPointIndex + 1)
            var i = 0
            while (i <= lastPointIndex) {
                flags[i] = r.readU8()
                if ((flags[i] and FLAG_REPEAT) != 0) {
                    val repeat = r.readU8()
                    for (j in 1..repeat) {
                        flags[i + j] = flags[i] and FLAG_REPEAT.inv()
                    }
                    i += repeat
                }
                i++
            }
            xCoordinates = IntArray(lastPointIndex + 1)
            var lastXCoordinate = 0
            for (j in 0..lastPointIndex) {
                if ((flags[j] and FLAG_X_SHORT_VECTOR) != 0) {
                    val sign = if ((flags[j] and FLAG_POSITIVE_X_SHORT_VECTOR) != 0) 1 else -1
                    xCoordinates[j] = lastXCoordinate + sign * r.readU8()
                    lastXCoordinate = xCoordinates[j]
                } else if ((flags[j] and FLAG_THIS_X_IS_SAME) != 0) {
                    xCoordinates[j] = lastXCoordinate
                } else {
                    xCoordinates[j] = lastXCoordinate + r.readI16BE()
                    lastXCoordinate = xCoordinates[j]
                }
            }
            yCoordinates = IntArray(lastPointIndex + 1)
            var lastYCoordinate = 0
            for (j in 0..lastPointIndex) {
                if ((flags[j] and FLAG_Y_SHORT_VECTOR) != 0) {
                    val sign = if ((flags[j] and FLAG_POSITIVE_Y_SHORT_VECTOR) != 0) 1 else -1
                    yCoordinates[j] = lastYCoordinate + sign * r.readU8()
                    lastYCoordinate = yCoordinates[j]
                } else if ((flags[j] and FLAG_THIS_Y_IS_SAME) != 0) {
                    yCoordinates[j] = lastYCoordinate
                } else {
                    yCoordinates[j] = lastYCoordinate + r.readI16BE()
                    lastYCoordinate = yCoordinates[j]
                }
            }
        } else if (numberOfContours == -1) {
            // Compound glyph
            numberOfComponents = 0
            val cFlags = mutableListOf<Int>()
            val cGlyphIndex = mutableListOf<Int>()
            val cArg1 = mutableListOf<Int>()
            val cArg2 = mutableListOf<Int>()
            val cTransA = mutableListOf<Double>()
            val cTransB = mutableListOf<Double>()
            val cTransC = mutableListOf<Double>()
            val cTransD = mutableListOf<Double>()

            while (true) {
                val fl = r.readU16BE()
                val words = (fl and COMPONENT_FLAG_ARG_1_AND_2_ARE_WORDS) != 0
                val signed = (fl and COMPONENT_FLAG_ARGS_ARE_XY_VALUES) != 0
                val scale = (fl and COMPONENT_FLAG_WE_HAVE_A_SCALE) != 0
                val xyScale = (fl and COMPONENT_FLAG_WE_HAVE_AN_X_AND_Y_SCALE) != 0
                val twoByTwo = (fl and COMPONENT_FLAG_WE_HAVE_A_TWO_BY_TWO) != 0
                val more = (fl and COMPONENT_FLAG_MORE_COMPONENTS) != 0

                numberOfComponents++
                cFlags.add(fl)
                cGlyphIndex.add(r.readU16BE())

                if (words) {
                    if (signed) {
                        cArg1.add(r.readI16BE())
                        cArg2.add(r.readI16BE())
                    } else {
                        cArg1.add(r.readU16BE())
                        cArg2.add(r.readU16BE())
                    }
                } else {
                    if (signed) {
                        cArg1.add(r.readI8())
                        cArg2.add(r.readI8())
                    } else {
                        cArg1.add(r.readU8())
                        cArg2.add(r.readU8())
                    }
                }

                val transformA: Double
                val transformB: Double
                val transformC: Double
                val transformD: Double
                if (twoByTwo) {
                    transformA = f2dot14Decode(r.readI16BE())
                    transformB = f2dot14Decode(r.readI16BE())
                    transformC = f2dot14Decode(r.readI16BE())
                    transformD = f2dot14Decode(r.readI16BE())
                } else if (xyScale) {
                    transformA = f2dot14Decode(r.readI16BE())
                    transformB = 0.0
                    transformC = 0.0
                    transformD = f2dot14Decode(r.readI16BE())
                } else if (scale) {
                    transformA = f2dot14Decode(r.readI16BE())
                    transformB = 0.0
                    transformC = 0.0
                    transformD = transformA
                } else {
                    transformA = 1.0
                    transformB = 0.0
                    transformC = 0.0
                    transformD = 1.0
                }
                cTransA.add(transformA)
                cTransB.add(transformB)
                cTransC.add(transformC)
                cTransD.add(transformD)

                if (!more) break
            }

            componentFlags = cFlags.toIntArray()
            componentGlyphIndex = cGlyphIndex.toIntArray()
            componentArgument1 = cArg1.toIntArray()
            componentArgument2 = cArg2.toIntArray()
            componentTransformA = cTransA.toDoubleArray()
            componentTransformB = cTransB.toDoubleArray()
            componentTransformC = cTransC.toDoubleArray()
            componentTransformD = cTransD.toDoubleArray()
        } else {
            error("Unknown glyph format: numberOfContours=$numberOfContours")
        }
    }

    public companion object {
        // Simple glyph flags
        public const val FLAG_ON_CURVE: Int = 0x01
        public const val FLAG_X_SHORT_VECTOR: Int = 0x02
        public const val FLAG_Y_SHORT_VECTOR: Int = 0x04
        public const val FLAG_REPEAT: Int = 0x08
        public const val FLAG_THIS_X_IS_SAME: Int = 0x10
        public const val FLAG_POSITIVE_X_SHORT_VECTOR: Int = 0x10
        public const val FLAG_THIS_Y_IS_SAME: Int = 0x20
        public const val FLAG_POSITIVE_Y_SHORT_VECTOR: Int = 0x20

        // Compound glyph flags
        public const val COMPONENT_FLAG_ARG_1_AND_2_ARE_WORDS: Int = 0x0001
        public const val COMPONENT_FLAG_ARGS_ARE_XY_VALUES: Int = 0x0002
        public const val COMPONENT_FLAG_ROUND_XY_TO_GRID: Int = 0x0004
        public const val COMPONENT_FLAG_WE_HAVE_A_SCALE: Int = 0x0008
        public const val COMPONENT_FLAG_MORE_COMPONENTS: Int = 0x0020
        public const val COMPONENT_FLAG_WE_HAVE_AN_X_AND_Y_SCALE: Int = 0x0040
        public const val COMPONENT_FLAG_WE_HAVE_A_TWO_BY_TWO: Int = 0x0080
        public const val COMPONENT_FLAG_WE_HAVE_INSTRUCTIONS: Int = 0x0100
        public const val COMPONENT_FLAG_USE_MY_METRICS: Int = 0x0200
        public const val COMPONENT_FLAG_OVERLAP_COMPOUND: Int = 0x0400

        /**
         * Encode a double as a 2.14 fixed-point value.
         * Matches the Java frozen code's encoding logic.
         */
        private fun f2dot14Encode(value: Double): Int {
            val raw = (value * 16384.0).roundToInt()
            return if (value < 0) raw + 0x8000 else raw
        }

        /**
         * Decode a 2.14 fixed-point value (read as a signed 16-bit int)
         * back to a double. Matches the Java frozen code's decoding logic:
         * `signum(v) * (v & 0x7FFF) / 16384.0`
         */
        private fun f2dot14Decode(v: Int): Double {
            val sign = if (v < 0) -1.0 else if (v > 0) 1.0 else 0.0
            return sign * (v and 0x7FFF) / 16384.0
        }
    }
}
