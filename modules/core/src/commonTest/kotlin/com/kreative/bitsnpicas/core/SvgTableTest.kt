package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.truetype.SvgTable
import com.kreative.bitsnpicas.core.truetype.SvgTableEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SvgTableTest {

    @Test
    fun roundTripSingleEntry() {
        val svg = SvgTable()
        val entry = SvgTableEntry()
        entry.startGlyphID = 1
        entry.endGlyphID = 1
        val doc = "<svg><circle r='10'/></svg>".encodeToByteArray()
        entry.svgDocument = doc
        svg.entries.add(entry)

        val compiled = svg.compile()
        val svg2 = SvgTable()
        svg2.decompile(compiled)

        assertEquals(1, svg2.entries.size)
        assertEquals(1, svg2.entries[0].startGlyphID)
        assertEquals(1, svg2.entries[0].endGlyphID)
        assertEquals(doc.decodeToString(), svg2.entries[0].svgDocument.decodeToString())
    }

    @Test
    fun roundTripMultipleEntries() {
        val svg = SvgTable()

        val doc1 = "<svg>glyph1</svg>".encodeToByteArray()
        val doc2 = "<svg>glyph2</svg>".encodeToByteArray()

        svg.entries.add(SvgTableEntry().apply {
            startGlyphID = 5; endGlyphID = 10; svgDocument = doc1
        })
        svg.entries.add(SvgTableEntry().apply {
            startGlyphID = 15; endGlyphID = 20; svgDocument = doc2
        })

        val compiled = svg.compile()
        val svg2 = SvgTable()
        svg2.decompile(compiled)

        assertEquals(2, svg2.entries.size)
        assertEquals(5, svg2.entries[0].startGlyphID)
        assertEquals(10, svg2.entries[0].endGlyphID)
        assertEquals(doc1.decodeToString(), svg2.entries[0].svgDocument.decodeToString())
        assertEquals(15, svg2.entries[1].startGlyphID)
        assertEquals(20, svg2.entries[1].endGlyphID)
        assertEquals(doc2.decodeToString(), svg2.entries[1].svgDocument.decodeToString())
    }

    @Test
    fun roundTripEmpty() {
        val svg = SvgTable()
        val compiled = svg.compile()
        val svg2 = SvgTable()
        svg2.decompile(compiled)
        assertEquals(0, svg2.entries.size)
    }

    @Test
    fun isCompressedDetection() {
        val entry = SvgTableEntry()
        entry.svgDocument = ByteArray(20)
        assertTrue(!entry.isCompressed())

        // Set gzip magic bytes
        entry.svgDocument[0] = 0x1F.toByte()
        entry.svgDocument[1] = 0x8B.toByte()
        assertTrue(entry.isCompressed())
    }

    @Test
    fun tableIdMatchesTag() {
        val t = SvgTable()
        assertEquals("SVG ", t.tableName)
        assertEquals(0x53564720, t.tableId)
    }
}
