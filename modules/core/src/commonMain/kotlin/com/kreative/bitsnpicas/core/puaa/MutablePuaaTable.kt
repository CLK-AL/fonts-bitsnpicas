package com.kreative.bitsnpicas.core.puaa

/**
 * Mutable builder for [PuaaTable], used by [PuaaCodec.compile] to
 * accumulate subtable entries during text-format parsing.
 */
public class MutablePuaaTable(version: Int = PuaaTable.DEFAULT_VERSION) {

    public var version: Int = version
        private set

    private val subtables = mutableMapOf<String, MutableList<PuaaEntry>>()

    /**
     * Get or create a mutable entry list for the named property.
     * Returns the list so callers can append entries.
     */
    public fun getOrCreateSubtable(property: String): MutableList<PuaaEntry> {
        return subtables.getOrPut(property) { mutableListOf() }
    }

    /**
     * Snapshot to an immutable [PuaaTable].
     */
    public fun toTable(): PuaaTable {
        return PuaaTable(
            version = version,
            subtables = subtables.map { (property, entries) ->
                PuaaSubtable(property, entries.toList())
            },
        )
    }
}
