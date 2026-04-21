package com.kreative.bitsnpicas.ui

import com.kreative.bitsnpicas.core.truetype.*
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Test for [FontBoxRenderer]: constructs a minimal synthetic TTF using
 * the commonMain [TrueTypeFile] API, then renders a glyph via FontBox
 * and asserts the output is non-empty.
 */
class FontBoxRendererTest {

    @Test
    fun `renderTtfGlyph produces non-empty bitmap for synthetic square glyph`() {
        val ttfBytes = buildMinimalTtf()
        val bitmap = FontBoxRenderer.renderTtfGlyph(ttfBytes, 0x41, 48)
        assertTrue(bitmap.width > 0, "Rendered bitmap width should be > 0")
        assertTrue(bitmap.height > 0, "Rendered bitmap height should be > 0")
        assertTrue(bitmap.pixels.any { it != 0 }, "Rendered bitmap should contain non-zero pixels")
    }

    /**
     * Build a minimal valid TTF containing:
     *   - glyph 0: .notdef (empty)
     *   - glyph 1: a filled square mapped to U+0041 ('A')
     *
     * Uses the commonMain TrueTypeFile + GlyfTableEntry to create the binary.
     */
    private fun buildMinimalTtf(): ByteArray {
        val unitsPerEm = 1000

        // ---- head ----
        val head = HeadTable().apply {
            this.unitsPerEm = unitsPerEm
            this.xMin = 0
            this.yMin = 0
            this.xMax = 800
            this.yMax = 800
            this.indexToLocFormat = HeadTable.INDEX_TO_LOC_FORMAT_LONG
            this.flags = 0x000B
            this.lowestRecPPEM = 8
        }

        // ---- maxp ----
        val maxp = MaxpTable().apply {
            numGlyphs = 2
            maxPoints = 4
            maxContours = 1
        }

        // ---- hhea ----
        val hhea = HheaTable().apply {
            ascent = 800
            descent = -200
            lineGap = 0
            advanceWidthMax = 1000
            numLongHorMetrics = 2
        }

        // ---- hmtx ----
        val hmtx = HmtxTable().apply {
            entries.add(HmtxTableEntry(advanceWidth = 500, leftSideBearing = 0))   // .notdef
            entries.add(HmtxTableEntry(advanceWidth = 1000, leftSideBearing = 100)) // 'A'
        }

        // ---- loca (offsets are set by the TrueTypeFile compile pipeline) ----
        val loca = LocaTable()

        // ---- glyf ----
        // Glyph 0: empty (.notdef)
        val emptyGlyph = ByteArray(0)

        // Glyph 1: simple square from (100,0) to (900,800)
        // 4 points, 1 contour, all on-curve
        val squareEntry = GlyfTableEntry().apply {
            numberOfContours = 1
            xMin = 100; yMin = 0; xMax = 900; yMax = 800
            endPointsOfContours = intArrayOf(3) // 4 points: 0,1,2,3
            instructions = IntArray(0)
            // All points are on-curve; using absolute coords:
            // (100, 0), (900, 0), (900, 800), (100, 800)
            // Flags: ON_CURVE only. We'll use short-vector encoding for simplicity.
            // Actually, easiest to just use non-short (16-bit) deltas everywhere.
            // flag = ON_CURVE (0x01) for all.
            flags = intArrayOf(0x01, 0x01, 0x01, 0x01)
            xCoordinates = intArrayOf(100, 900, 900, 100)
            yCoordinates = intArrayOf(0, 0, 800, 800)
        }

        val glyf = GlyfTable().apply {
            glyphs.add(emptyGlyph)
            glyphs.add(squareEntry.compile())
        }

        // Compute loca offsets
        var offset = 0
        for (g in glyf.glyphs) {
            loca.offsets.add(offset)
            offset += g.size
        }
        loca.offsets.add(offset) // end marker

        // ---- cmap ----
        val cmapSubtable = CmapSubtableFormat4().apply {
            languageID = 0
            val seqEntry = CmapSubtableSequentialEntry().apply {
                startCharCode = 0x0041
                endCharCode = 0x0041
                glyphIndex = 1
            }
            // Format 4 must end with a 0xFFFF sentinel segment
            val sentinel = CmapSubtableSequentialEntry().apply {
                startCharCode = 0xFFFF
                endCharCode = 0xFFFF
                glyphIndex = 0
            }
            entries.add(seqEntry)
            entries.add(sentinel)
        }
        val cmap = CmapTable().apply {
            version = 0
            subtables.add(cmapSubtable)
            // Windows / Unicode BMP (platform 3, encoding 1)
            entries.add(CmapTableEntry(platformID = 3, platformSpecificID = 1, subtable = cmapSubtable))
        }

        // ---- post ----
        val post = PostTable().apply {
            format = PostTable.FORMAT_3 // no glyph names
        }

        // ---- name ----
        val nameTable = NameTable().apply {
            format = NameTable.FORMAT_DEFAULT
            fun addName(nameID: Int, value: String) {
                val bytes = value.toByteArray(Charsets.UTF_8)
                val entry = NameTableEntry().apply {
                    this.platformID = 1 // Mac
                    this.platformSpecificID = 0
                    this.languageID = 0
                    this.nameID = nameID
                    this.nameData = bytes
                }
                entries.add(entry)
            }
            addName(0, "Public Domain")    // copyright
            addName(1, "TestFont")         // family name
            addName(2, "Regular")          // style
            addName(4, "TestFont Regular") // full name
            addName(5, "Version 1.0")     // version
            addName(6, "TestFont-Regular") // PostScript name
        }

        // ---- OS/2 ----
        val os2 = Os2Table().apply {
            this.typoAscent = 800
            this.typoDescent = -200
            this.winAscent = 800
            this.winDescent = 200
            this.fsFirstCharIndex = 0x0041
            this.fsLastCharIndex = 0x0041
        }

        // ---- Assemble ----
        val ttf = TrueTypeFile().apply {
            tables.add(head)
            tables.add(maxp)
            tables.add(hhea)
            tables.add(hmtx)
            tables.add(loca)
            tables.add(glyf)
            tables.add(cmap)
            tables.add(post)
            tables.add(nameTable)
            tables.add(os2)
        }

        return ttf.compile()
    }
}
