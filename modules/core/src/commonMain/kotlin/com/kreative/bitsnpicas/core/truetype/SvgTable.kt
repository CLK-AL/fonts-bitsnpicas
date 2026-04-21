package com.kreative.bitsnpicas.core.truetype

import com.kreative.bitsnpicas.core.ByteReader
import com.kreative.bitsnpicas.core.ByteWriter

/**
 * The `SVG ` table — SVG glyph descriptions.
 *
 * Stores compressed or uncompressed SVG documents per glyph range.
 * The table consists of a header, an SVG Document Index (array of
 * startGlyph/endGlyph/offset/length entries), and the SVG data block.
 *
 * Spec: https://docs.microsoft.com/en-us/typography/opentype/spec/svg
 */
public class SvgTable : TrueTypeTable() {

    override val tableName: String get() = "SVG "

    public val entries: MutableList<SvgTableEntry> = mutableListOf()

    override fun compile(dependencies: Map<Int, TrueTypeTable>): ByteArray {
        val w = ByteWriter()

        // SVG table header: version (u16), offsetToSVGDocumentList (u32), reserved (u32)
        w.writeU16BE(0)          // version
        w.writeIntBE(10)         // offsetToSVGDocumentList (points to index right after header)
        w.writeIntBE(0)          // reserved

        // SVG Document Index: numEntries (u16), then entries
        w.writeU16BE(entries.size)

        // Track which documents have been written (by identity via offset+length hash)
        // to deduplicate shared SVG documents.
        val documentOffsets = mutableMapOf<Long, Int>()
        var currentLocation = 2 + entries.size * 12 // offset within index block

        // First pass: write index entries
        for (e in entries) {
            w.writeU16BE(e.startGlyphID)
            w.writeU16BE(e.endGlyphID)
            val docKey = identityKey(e.svgDocument)
            val existingOffset = documentOffsets[docKey]
            if (existingOffset != null) {
                w.writeIntBE(existingOffset)
            } else {
                w.writeIntBE(currentLocation)
                documentOffsets[docKey] = currentLocation
                currentLocation += e.svgDocument.size
            }
            w.writeIntBE(e.svgDocument.size)
        }

        // Second pass: write document data (deduplicated)
        val writtenDocs = mutableSetOf<Long>()
        for (e in entries) {
            val docKey = identityKey(e.svgDocument)
            if (docKey !in writtenDocs) {
                w.writeBytes(e.svgDocument)
                writtenDocs.add(docKey)
            }
        }

        return w.toByteArray()
    }

    override fun decompile(data: ByteArray, dependencies: Map<Int, TrueTypeTable>) {
        val r = ByteReader(data)

        // Header
        /* version */ r.readU16BE()
        val offsetToIndex = r.readIntBE()
        // reserved (4 bytes) - skip if present; the index starts at offsetToIndex

        // Read SVG Document Index
        r.seek(offsetToIndex)
        val numEntries = r.readU16BE()

        val startGlyphIDs = IntArray(numEntries)
        val endGlyphIDs = IntArray(numEntries)
        val offsets = IntArray(numEntries)
        val lengths = IntArray(numEntries)

        for (i in 0 until numEntries) {
            startGlyphIDs[i] = r.readU16BE()
            endGlyphIDs[i] = r.readU16BE()
            offsets[i] = r.readIntBE()
            lengths[i] = r.readIntBE()
        }

        // Read document data (deduplicate by offset+length hash)
        val documents = mutableMapOf<Long, ByteArray>()
        for (i in 0 until numEntries) {
            val hash = (offsets[i].toLong() shl 32) or lengths[i].toLong()
            if (hash !in documents) {
                r.seek(offsetToIndex + offsets[i])
                documents[hash] = r.readBytes(lengths[i])
            }
        }

        // Build entries
        entries.clear()
        for (i in 0 until numEntries) {
            val e = SvgTableEntry()
            e.startGlyphID = startGlyphIDs[i]
            e.endGlyphID = endGlyphIDs[i]
            val hash = (offsets[i].toLong() shl 32) or lengths[i].toLong()
            e.svgDocument = documents[hash] ?: ByteArray(0)
            entries.add(e)
        }
    }

    private companion object {
        /** Create a deduplication key from offset within the write stream. */
        private fun identityKey(doc: ByteArray): Long {
            // Use content hash for deduplication
            var h = 0L
            for (b in doc) {
                h = h * 31 + (b.toInt() and 0xFF)
            }
            return (h shl 32) or doc.size.toLong()
        }
    }
}
