package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `Blocks.txt`.
 *
 * Format: `XXXX..YYYY; Block_Name`
 *
 * Ported from the frozen Java `com.kreative.bitsnpicas.puaa.BlocksCodec`.
 */
public class BlocksCodec : PuaaCodec {

    override val fileName: String = "Blocks.txt"
    override val propertyNames: List<String> = listOf("Block")

    override fun compile(table: MutablePuaaTable, lines: Sequence<String>) {
        val blocks = table.getOrCreateSubtable("Block")
        for (line in lines) {
            val fields = PuaaTextUtility.splitLine(line) ?: continue
            if (fields.size < 2) continue
            try {
                val r = PuaaTextUtility.splitRange(fields[0])
                val v = fields[1].trim()
                blocks.add(PuaaEntry.Single(r[0], r[1], v))
            } catch (_: NumberFormatException) {
                // skip malformed lines
            }
        }
    }

    override fun decompile(table: PuaaTable): List<String> {
        val blocks = table.subtables.firstOrNull { it.property == "Block" }
            ?: return emptyList()
        if (blocks.entries.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        for (e in blocks.entries) {
            val r = PuaaTextUtility.joinRange(e.firstCodePoint, e.lastCodePoint)
            val v = e.getPropertyValue(e.firstCodePoint) ?: continue
            result.add("$r; $v")
        }
        return result
    }
}
