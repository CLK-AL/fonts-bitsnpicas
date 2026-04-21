package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.truetype.ColrTable
import kotlin.test.Test
import kotlin.test.assertEquals

class ColrTableTest {

    @Test
    fun roundTripV0() {
        val colr = ColrTable()
        colr.version = 0

        val bg1 = ColrTable.BaseGlyph().apply {
            glyphID = 10; firstLayerIndex = 0; numLayers = 2
        }
        val bg2 = ColrTable.BaseGlyph().apply {
            glyphID = 20; firstLayerIndex = 2; numLayers = 3
        }
        colr.baseGlyphRecords.add(bg1)
        colr.baseGlyphRecords.add(bg2)

        colr.layerRecords.add(ColrTable.Layer().apply { glyphID = 100; paletteIndex = 0 })
        colr.layerRecords.add(ColrTable.Layer().apply { glyphID = 101; paletteIndex = 1 })
        colr.layerRecords.add(ColrTable.Layer().apply { glyphID = 102; paletteIndex = 0 })
        colr.layerRecords.add(ColrTable.Layer().apply { glyphID = 103; paletteIndex = 2 })
        colr.layerRecords.add(ColrTable.Layer().apply { glyphID = 104; paletteIndex = 3 })

        val compiled = colr.compile()
        val colr2 = ColrTable()
        colr2.decompile(compiled)

        assertEquals(0, colr2.version)
        assertEquals(2, colr2.baseGlyphRecords.size)
        assertEquals(10, colr2.baseGlyphRecords[0].glyphID)
        assertEquals(0, colr2.baseGlyphRecords[0].firstLayerIndex)
        assertEquals(2, colr2.baseGlyphRecords[0].numLayers)
        assertEquals(20, colr2.baseGlyphRecords[1].glyphID)
        assertEquals(2, colr2.baseGlyphRecords[1].firstLayerIndex)
        assertEquals(3, colr2.baseGlyphRecords[1].numLayers)

        assertEquals(5, colr2.layerRecords.size)
        assertEquals(100, colr2.layerRecords[0].glyphID)
        assertEquals(0, colr2.layerRecords[0].paletteIndex)
        assertEquals(104, colr2.layerRecords[4].glyphID)
        assertEquals(3, colr2.layerRecords[4].paletteIndex)
    }

    @Test
    fun roundTripEmpty() {
        val colr = ColrTable()
        colr.version = 0
        val compiled = colr.compile()
        val colr2 = ColrTable()
        colr2.decompile(compiled)
        assertEquals(0, colr2.version)
        assertEquals(0, colr2.baseGlyphRecords.size)
        assertEquals(0, colr2.layerRecords.size)
    }

    @Test
    fun tableIdMatchesTag() {
        val t = ColrTable()
        assertEquals("COLR", t.tableName)
        assertEquals(0x434F4C52, t.tableId)
    }
}
