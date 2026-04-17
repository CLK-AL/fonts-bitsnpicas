package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.BitmapFont as JavaBitmapFont
import com.kreative.bitsnpicas.BitmapFontGlyph as JavaBitmapFontGlyph
import com.kreative.bitsnpicas.Font as JavaFont
import com.kreative.bitsnpicas.exporter.BDFBitmapFontExporter
import com.kreative.bitsnpicas.exporter.FNTBitmapFontExporter
import com.kreative.bitsnpicas.exporter.HexBitmapFontExporter
import com.kreative.bitsnpicas.exporter.PSFBitmapFontExporter
import com.kreative.bitsnpicas.importer.BDFBitmapFontImporter
import com.kreative.bitsnpicas.importer.FNTBitmapFontImporter
import com.kreative.bitsnpicas.importer.HexBitmapFontImporter
import com.kreative.bitsnpicas.importer.PSFBitmapFontImporter
import com.kreative.bitsnpicas.core.puaa.PuaaEntry
import com.kreative.bitsnpicas.core.puaa.PuaaSubtable
import com.kreative.bitsnpicas.core.puaa.PuaaTable
import com.kreative.bitsnpicas.truetype.PuaaTable as JavaPuaaTable
import com.kreative.bitsnpicas.truetype.PuaaSubtable as JavaPuaaSubtable
import com.kreative.bitsnpicas.truetype.PuaaSubtableEntry as JavaPuaaSubtableEntry
import com.kreative.bitsnpicas.truetype.TrueTypeTable as JavaTrueTypeTable
import com.kreative.bitsnpicas.truetype.TrueTypeFile as JavaTrueTypeFile
import com.kreative.bitsnpicas.truetype.HeadTable as JavaHeadTable
import com.kreative.bitsnpicas.truetype.NameTable as JavaNameTable
import com.kreative.bitsnpicas.truetype.NameTableEntry as JavaNameTableEntry
import com.kreative.bitsnpicas.truetype.PostTable as JavaPostTable
import com.kreative.bitsnpicas.truetype.PostTableEntry as JavaPostTableEntry
import com.kreative.bitsnpicas.core.truetype.TrueTypeFile as KTrueTypeFile
import com.kreative.bitsnpicas.core.truetype.HeadTable as KHeadTable
import com.kreative.bitsnpicas.core.truetype.NameTable as KNameTable
import com.kreative.bitsnpicas.core.truetype.NameTableEntry as KNameTableEntry
import com.kreative.bitsnpicas.core.truetype.PostTable as KPostTable
import com.kreative.bitsnpicas.core.truetype.PostTableEntry as KPostTableEntry
import com.kreative.bitsnpicas.core.truetype.UnknownTable as KUnknownTable

/**
 * JVM-only adapters that bridge the frozen Java `BitmapFontGlyph`
 * into the commonMain `BitmapGlyph` API. Used by
 * BitmapGlyphComposeJvmParityTest to drive the same fixtures through
 * both implementations and assert byte-exact parity.
 *
 * This file never becomes commonMain — it exists only for the
 * duration of Stage S4 so the `jvmTest` side can perform
 * differential-parity checks. Deleted once every format module has
 * reached parity and the legacy Java profile is retired.
 */
public object JavaLegacyAdapter {

    /** Lift a commonMain BitmapGlyph into a frozen-Java BitmapFontGlyph. */
    public fun toJava(g: BitmapGlyph): JavaBitmapFontGlyph {
        val javaBytes: Array<ByteArray> = Array(g.bitmap.size) { i ->
            val row = g.bitmap[i]
            ByteArray(row.size) { j -> row[j].toByte() }
        }
        // Public 4-arg constructor: (glyph, offset=x, width=advance, ascent=y)
        return JavaBitmapFontGlyph(javaBytes, g.x, g.advance, g.y)
    }

    /** Lower a frozen-Java BitmapFontGlyph result back into a commonMain BitmapGlyph. */
    public fun fromJava(g: JavaBitmapFontGlyph?): BitmapGlyph? {
        if (g == null) return null
        val rows: Array<ByteArray> = g.glyph ?: return null
        val kRows: List<IntArray> = rows.map { row ->
            IntArray(row.size) { j -> (row[j].toInt()) and 0xFF }
        }
        return BitmapGlyph(
            bitmap = kRows,
            x = g.x,
            // BitmapFontGlyph exposes the advance via getCharacterWidth().
            advance = g.characterWidth,
            // g.y is protected — use the public getter, which returns the ascent/y.
            y = g.y,
        )
    }

    /**
     * Import a PSF font via the frozen Java PSFBitmapFontImporter,
     * then convert the result to commonMain types for parity testing.
     */
    public fun importPsfViaJava(bytes: ByteArray): BitmapFont {
        val importer = PSFBitmapFontImporter()
        val javaFonts: Array<JavaBitmapFont> = importer.importFont(bytes)
        val jf = javaFonts[0]

        val glyphs = mutableMapOf<Int, BitmapGlyph>()
        for ((cp, jGlyph) in jf.characters(true)) {
            val kg = fromJava(jGlyph) ?: continue
            glyphs[cp] = kg
        }

        return BitmapFont(
            glyphs = glyphs,
            emAscent = jf.emAscent,
            emDescent = jf.emDescent,
            lineAscent = jf.lineAscent,
            lineDescent = jf.lineDescent,
            xHeight = jf.xHeight,
            capHeight = jf.capHeight,
            lineGap = jf.lineGap,
            newGlyphWidth = jf.newGlyphWidth,
        )
    }

    /**
     * Import an FNT font via the frozen Java FNTBitmapFontImporter,
     * then convert the result to commonMain types for parity testing.
     */
    public fun importFntViaJava(bytes: ByteArray): BitmapFont {
        val importer = FNTBitmapFontImporter()
        val javaFonts: Array<JavaBitmapFont> = importer.importFont(bytes)
        val jf = javaFonts[0]

        val glyphs = mutableMapOf<Int, BitmapGlyph>()
        for ((cp, jGlyph) in jf.characters(true)) {
            val kg = fromJava(jGlyph) ?: continue
            glyphs[cp] = kg
        }

        return BitmapFont(
            glyphs = glyphs,
            emAscent = jf.emAscent,
            emDescent = jf.emDescent,
            lineAscent = jf.lineAscent,
            lineDescent = jf.lineDescent,
            xHeight = jf.xHeight,
            capHeight = jf.capHeight,
            lineGap = jf.lineGap,
            newGlyphWidth = jf.newGlyphWidth,
        )
    }

    /**
     * Import a BDF font via the frozen Java BDFBitmapFontImporter,
     * then convert the result to commonMain types for parity testing.
     * Takes the full BDF file as a single String (identical input as the
     * commonMain `BdfImporter.read`).
     */
    public fun importBdfViaJava(text: String): BitmapFont {
        val importer = BDFBitmapFontImporter()
        val javaFonts: Array<JavaBitmapFont> = importer.importFont(
            text.toByteArray(Charsets.UTF_8)
        )
        val jf = javaFonts[0]

        val glyphs = mutableMapOf<Int, BitmapGlyph>()
        for ((cp, jGlyph) in jf.characters(true)) {
            val kg = fromJava(jGlyph) ?: continue
            glyphs[cp] = kg
        }

        return BitmapFont(
            glyphs = glyphs,
            emAscent = jf.emAscent,
            emDescent = jf.emDescent,
            lineAscent = jf.lineAscent,
            lineDescent = jf.lineDescent,
            xHeight = jf.xHeight,
            capHeight = jf.capHeight,
            lineGap = jf.lineGap,
            newGlyphWidth = jf.newGlyphWidth,
            name = jf.getName(JavaFont.NAME_FAMILY),
        )
    }

    /**
     * Export a commonMain [BitmapFont] via the frozen Java
     * `BDFBitmapFontExporter`. Used by the jvmTest parity gate to
     * compare commonMain exporter output against the legacy path.
     *
     * This adapter bridges commonMain -> Java: it reconstructs a Java
     * `BitmapFont` from the commonMain model, populating just enough
     * state for the Java exporter to run (font metrics + per-codepoint
     * glyphs). BDF output is ASCII-safe, so we decode the Java byte
     * array as UTF-8.
     */
    public fun exportBdfViaJava(font: BitmapFont): String {
        val jf = toJavaFont(font)
        val exporter = BDFBitmapFontExporter()
        val bytes = exporter.exportFontToBytes(jf)
        return String(bytes, Charsets.UTF_8)
    }

    /**
     * Import a Hex bitmap font via the frozen Java HexBitmapFontImporter,
     * then convert the result to commonMain types for parity testing.
     * Takes the full hex file as a single String.
     */
    public fun importHexViaJava(text: String): BitmapFont {
        val importer = HexBitmapFontImporter()
        val javaFonts: Array<JavaBitmapFont> = importer.importFont(
            text.toByteArray(Charsets.UTF_8)
        )
        if (javaFonts.isEmpty()) {
            // Java importer returns empty array for empty fonts
            return BitmapFont(
                glyphs = emptyMap(),
                emAscent = 7,
                emDescent = 1,
                lineAscent = 7,
                lineDescent = 1,
                xHeight = 5,
                capHeight = 7,
                lineGap = 0,
                newGlyphWidth = 8,
            )
        }
        val jf = javaFonts[0]

        val glyphs = mutableMapOf<Int, BitmapGlyph>()
        for ((cp, jGlyph) in jf.characters(true)) {
            val kg = fromJava(jGlyph) ?: continue
            glyphs[cp] = kg
        }

        return BitmapFont(
            glyphs = glyphs,
            emAscent = jf.emAscent,
            emDescent = jf.emDescent,
            lineAscent = jf.lineAscent,
            lineDescent = jf.lineDescent,
            xHeight = jf.xHeight,
            capHeight = jf.capHeight,
            lineGap = jf.lineGap,
            newGlyphWidth = jf.newGlyphWidth,
        )
    }

    /**
     * Export a commonMain [BitmapFont] via the frozen Java
     * `PSFBitmapFontExporter`. Used by the jvmTest parity gate to
     * compare commonMain exporter output against the legacy path.
     */
    public fun exportPsfViaJava(font: BitmapFont): ByteArray {
        val jf = toJavaFont(font)
        val exporter = PSFBitmapFontExporter()
        return exporter.exportFontToBytes(jf)
    }

    /**
     * Export a commonMain [BitmapFont] via the frozen Java
     * `FNTBitmapFontExporter`. Used by the jvmTest parity gate.
     */
    public fun exportFntViaJava(font: BitmapFont): ByteArray {
        val jf = toJavaFont(font)
        val exporter = FNTBitmapFontExporter()
        return exporter.exportFontToBytes(jf)
    }

    /**
     * Export a commonMain [BitmapFont] via the frozen Java
     * `HexBitmapFontExporter`. Used by the jvmTest parity gate.
     */
    public fun exportHexViaJava(font: BitmapFont): String {
        val jf = toJavaFont(font)
        val exporter = HexBitmapFontExporter()
        val bytes = exporter.exportFontToBytes(jf)
        return String(bytes, Charsets.UTF_8)
    }

    /**
     * Bridge a commonMain [BitmapFont] into a frozen Java `BitmapFont`,
     * populating the fields needed for the Java exporters.
     */
    private fun toJavaFont(font: BitmapFont): JavaBitmapFont {
        val jf = JavaBitmapFont()
        jf.setEmAscent(font.emAscent)
        jf.setEmDescent(font.emDescent)
        jf.setLineAscent(font.lineAscent)
        jf.setLineDescent(font.lineDescent)
        jf.setXHeight(font.xHeight)
        jf.setCapHeight(font.capHeight)
        if (font.name != null) {
            jf.setName(JavaFont.NAME_FAMILY, font.name)
        }
        for ((cp, kGlyph) in font.glyphs) {
            jf.putCharacter(cp, toJava(kGlyph))
        }
        return jf
    }

    /** Compose via the frozen Java code path, returning a commonMain view. */
    public fun composeViaJava(glyphs: List<BitmapGlyph?>): BitmapGlyph? {
        val javaArr: Array<JavaBitmapFontGlyph?> = Array(glyphs.size) { i ->
            glyphs[i]?.let { toJava(it) }
        }
        return fromJava(JavaBitmapFontGlyph.compose(*javaArr))
    }

    // ---- PUAA table adapters ------------------------------------------------

    /**
     * Compile a commonMain [PuaaTable] via the frozen Java `PuaaTable`.
     * Bridges commonMain types into Java, compiles via the Java path,
     * and returns the raw binary output.
     */
    public fun compilePuaaViaJava(table: PuaaTable): ByteArray {
        val jt = toJavaPuaaTable(table)
        return jt.compile(emptyArray<JavaTrueTypeTable>())
    }

    /**
     * Decompile raw PUAA bytes via the frozen Java `PuaaTable`,
     * then convert the result back to commonMain types.
     */
    public fun decompilePuaaViaJava(data: ByteArray): PuaaTable {
        val jt = JavaPuaaTable()
        jt.decompile(data, emptyArray<JavaTrueTypeTable>())
        return fromJavaPuaaTable(jt)
    }

    /** Convert a commonMain PuaaTable to a frozen Java PuaaTable. */
    private fun toJavaPuaaTable(table: PuaaTable): JavaPuaaTable {
        val jt = JavaPuaaTable()
        jt.version = table.version
        for (st in table.subtables) {
            val jst = JavaPuaaSubtable()
            jst.property = st.property
            for (entry in st.entries) {
                jst.add(toJavaEntry(entry))
            }
            jt.add(jst)
        }
        return jt
    }

    private fun toJavaEntry(e: PuaaEntry): JavaPuaaSubtableEntry {
        return when (e) {
            is PuaaEntry.Single -> {
                val je = JavaPuaaSubtableEntry.Single()
                je.firstCodePoint = e.firstCodePoint
                je.lastCodePoint = e.lastCodePoint
                je.value = e.value
                je
            }
            is PuaaEntry.Multiple -> {
                val je = JavaPuaaSubtableEntry.Multiple()
                je.firstCodePoint = e.firstCodePoint
                je.lastCodePoint = e.lastCodePoint
                je.values = e.values.toTypedArray()
                je
            }
            is PuaaEntry.BooleanEntry -> {
                val je = JavaPuaaSubtableEntry.Boolean()
                je.firstCodePoint = e.firstCodePoint
                je.lastCodePoint = e.lastCodePoint
                je.value = e.value
                je
            }
            is PuaaEntry.Decimal -> {
                val je = JavaPuaaSubtableEntry.Decimal()
                je.firstCodePoint = e.firstCodePoint
                je.lastCodePoint = e.lastCodePoint
                je.value = e.value
                je
            }
            is PuaaEntry.Hexadecimal -> {
                val je = JavaPuaaSubtableEntry.Hexadecimal()
                je.firstCodePoint = e.firstCodePoint
                je.lastCodePoint = e.lastCodePoint
                je.value = e.value
                je
            }
            is PuaaEntry.HexMultiple -> {
                val je = JavaPuaaSubtableEntry.HexMultiple()
                je.firstCodePoint = e.firstCodePoint
                je.lastCodePoint = e.lastCodePoint
                je.values = e.values.copyOf()
                je
            }
            is PuaaEntry.HexSequence -> {
                val je = JavaPuaaSubtableEntry.HexSequence()
                je.firstCodePoint = e.firstCodePoint
                je.lastCodePoint = e.lastCodePoint
                je.values = e.values.copyOf()
                je
            }
            is PuaaEntry.CaseMapping -> {
                val je = JavaPuaaSubtableEntry.CaseMapping()
                je.firstCodePoint = e.firstCodePoint
                je.lastCodePoint = e.lastCodePoint
                je.values = e.values.copyOf()
                je.condition = e.condition
                je
            }
            is PuaaEntry.NameAlias -> {
                val je = JavaPuaaSubtableEntry.NameAlias()
                je.firstCodePoint = e.firstCodePoint
                je.lastCodePoint = e.lastCodePoint
                je.alias = e.alias
                je.type = e.type
                je
            }
        }
    }

    /** Convert a frozen Java PuaaTable back to commonMain types. */
    private fun fromJavaPuaaTable(jt: JavaPuaaTable): PuaaTable {
        val subtables = mutableListOf<PuaaSubtable>()
        for (jst in jt) {
            val entries = mutableListOf<PuaaEntry>()
            for (je in jst) {
                entries.add(fromJavaEntry(je))
            }
            subtables.add(PuaaSubtable(jst.property, entries))
        }
        return PuaaTable(version = jt.version, subtables = subtables)
    }

    private fun fromJavaEntry(je: JavaPuaaSubtableEntry): PuaaEntry {
        return when (je) {
            is JavaPuaaSubtableEntry.Single -> PuaaEntry.Single(
                je.firstCodePoint, je.lastCodePoint, je.value,
            )
            is JavaPuaaSubtableEntry.Multiple -> PuaaEntry.Multiple(
                je.firstCodePoint, je.lastCodePoint, je.values?.toList() ?: emptyList(),
            )
            is JavaPuaaSubtableEntry.Boolean -> PuaaEntry.BooleanEntry(
                je.firstCodePoint, je.lastCodePoint, je.value,
            )
            is JavaPuaaSubtableEntry.Decimal -> PuaaEntry.Decimal(
                je.firstCodePoint, je.lastCodePoint, je.value,
            )
            is JavaPuaaSubtableEntry.Hexadecimal -> PuaaEntry.Hexadecimal(
                je.firstCodePoint, je.lastCodePoint, je.value,
            )
            is JavaPuaaSubtableEntry.HexMultiple -> PuaaEntry.HexMultiple(
                je.firstCodePoint, je.lastCodePoint, je.values?.copyOf() ?: intArrayOf(),
            )
            is JavaPuaaSubtableEntry.HexSequence -> PuaaEntry.HexSequence(
                je.firstCodePoint, je.lastCodePoint, je.values?.copyOf() ?: intArrayOf(),
            )
            is JavaPuaaSubtableEntry.CaseMapping -> PuaaEntry.CaseMapping(
                je.firstCodePoint, je.lastCodePoint,
                je.values?.copyOf() ?: intArrayOf(), je.condition,
            )
            is JavaPuaaSubtableEntry.NameAlias -> PuaaEntry.NameAlias(
                je.firstCodePoint, je.lastCodePoint, je.alias, je.type,
            )
            else -> throw IllegalArgumentException("Unknown Java PUAA entry type: ${je.javaClass}")
        }
    }

    // ---- TrueType table adapters ------------------------------------------------

    /**
     * Compile a TTF via the frozen Java TrueTypeFile path.
     * Builds a Java TrueTypeFile from the commonMain model, compiles
     * via the Java compile(), and returns the raw bytes.
     */
    public fun compileTtfViaJava(file: KTrueTypeFile): ByteArray {
        val jf = JavaTrueTypeFile()
        jf.scaler = file.scaler
        for (table in file.tables) {
            jf.add(toJavaTrueTypeTable(table))
        }
        return jf.compile()
    }

    /**
     * Decompile a TTF binary via the frozen Java TrueTypeFile path,
     * then convert the result back to commonMain types.
     */
    public fun decompileTtfViaJava(data: ByteArray): KTrueTypeFile {
        val jf = JavaTrueTypeFile()
        jf.decompile(data)
        return fromJavaTrueTypeFile(jf)
    }

    /** Convert a commonMain TrueTypeTable to a frozen Java TrueTypeTable. */
    private fun toJavaTrueTypeTable(table: com.kreative.bitsnpicas.core.truetype.TrueTypeTable): JavaTrueTypeTable {
        return when (table) {
            is KHeadTable -> {
                val jt = JavaHeadTable()
                jt.version = table.version
                jt.fontRevision = table.fontRevision
                jt.checkSum = table.checkSum
                jt.magicNumber = table.magicNumber
                jt.flags = table.flags
                jt.unitsPerEm = table.unitsPerEm
                jt.dateCreated = table.dateCreated
                jt.dateModified = table.dateModified
                jt.xMin = table.xMin
                jt.yMin = table.yMin
                jt.xMax = table.xMax
                jt.yMax = table.yMax
                jt.macStyle = table.macStyle
                jt.lowestRecPPEM = table.lowestRecPPEM
                jt.fontDirectionHint = table.fontDirectionHint
                jt.indexToLocFormat = table.indexToLocFormat
                jt.glyphDataFormat = table.glyphDataFormat
                jt
            }
            is KNameTable -> {
                val jt = JavaNameTable()
                jt.format = table.format
                for (e in table.entries) {
                    val je = JavaNameTableEntry()
                    je.index = e.index
                    je.platformID = e.platformID
                    je.platformSpecificID = e.platformSpecificID
                    je.languageID = e.languageID
                    je.nameID = e.nameID
                    je.nameData = e.nameData.copyOf()
                    je.padding = e.padding
                    jt.add(je)
                }
                jt
            }
            is KPostTable -> {
                val jt = JavaPostTable()
                jt.format = table.format
                jt.italicAngle = table.italicAngle
                jt.underlinePosition = table.underlinePosition
                jt.underlineThickness = table.underlineThickness
                jt.fixedPitch = table.fixedPitch
                jt.minMemType42 = table.minMemType42
                jt.maxMemType42 = table.maxMemType42
                jt.minMemType1 = table.minMemType1
                jt.maxMemType1 = table.maxMemType1
                for (e in table.entries) {
                    if (e.isInteger()) {
                        jt.add(JavaPostTableEntry(e.intValue()))
                    } else {
                        jt.add(JavaPostTableEntry(e.stringValue()))
                    }
                }
                jt
            }
            is KUnknownTable -> {
                val jt = com.kreative.bitsnpicas.truetype.UnknownTable(table.tableName, table.data.copyOf())
                jt
            }
            else -> error("Unknown table type: ${table::class}")
        }
    }

    /** Convert a frozen Java TrueTypeFile back to commonMain types. */
    private fun fromJavaTrueTypeFile(jf: JavaTrueTypeFile): KTrueTypeFile {
        val file = KTrueTypeFile()
        file.scaler = jf.scaler
        for (jt in jf) {
            file.tables.add(fromJavaTrueTypeTable(jt))
        }
        return file
    }

    /** Convert a frozen Java TrueTypeTable back to commonMain. */
    private fun fromJavaTrueTypeTable(jt: JavaTrueTypeTable): com.kreative.bitsnpicas.core.truetype.TrueTypeTable {
        return when (jt) {
            is JavaHeadTable -> {
                val t = KHeadTable()
                t.version = jt.version
                t.fontRevision = jt.fontRevision
                t.checkSum = jt.checkSum
                t.magicNumber = jt.magicNumber
                t.flags = jt.flags
                t.unitsPerEm = jt.unitsPerEm
                t.dateCreated = jt.dateCreated
                t.dateModified = jt.dateModified
                t.xMin = jt.xMin
                t.yMin = jt.yMin
                t.xMax = jt.xMax
                t.yMax = jt.yMax
                t.macStyle = jt.macStyle
                t.lowestRecPPEM = jt.lowestRecPPEM
                t.fontDirectionHint = jt.fontDirectionHint
                t.indexToLocFormat = jt.indexToLocFormat
                t.glyphDataFormat = jt.glyphDataFormat
                t
            }
            is JavaNameTable -> {
                val t = KNameTable()
                t.format = jt.format
                for (je in jt) {
                    val e = KNameTableEntry()
                    e.index = je.index
                    e.platformID = je.platformID
                    e.platformSpecificID = je.platformSpecificID
                    e.languageID = je.languageID
                    e.nameID = je.nameID
                    e.nameData = je.nameData.copyOf()
                    e.padding = je.padding
                    t.entries.add(e)
                }
                t
            }
            is JavaPostTable -> {
                val t = KPostTable()
                t.format = jt.format
                t.italicAngle = jt.italicAngle
                t.underlinePosition = jt.underlinePosition
                t.underlineThickness = jt.underlineThickness
                t.fixedPitch = jt.fixedPitch
                t.minMemType42 = jt.minMemType42
                t.maxMemType42 = jt.maxMemType42
                t.minMemType1 = jt.minMemType1
                t.maxMemType1 = jt.maxMemType1
                for (je in jt) {
                    if (je.isInteger) {
                        t.entries.add(KPostTableEntry(je.intValue()))
                    } else {
                        t.entries.add(KPostTableEntry(je.stringValue()))
                    }
                }
                t
            }
            else -> {
                // Fall back to UnknownTable for unrecognized Java tables
                val rawData = jt.compile(emptyArray<JavaTrueTypeTable>())
                KUnknownTable(jt.tableName(), rawData)
            }
        }
    }
}
