package com.kreative.bitsnpicas.core.puaa

/**
 * Text-format codec interface for PUAA property files.
 *
 * Each codec reads/writes one specific Unicode property file format
 * (e.g. `Blocks.txt`, `PropList.txt`, `UnicodeData.txt`).
 *
 * Ported from the frozen Java `com.kreative.bitsnpicas.puaa.PuaaCodec`.
 */
public interface PuaaCodec : Comparable<PuaaCodec> {

    /** The canonical filename this codec handles (e.g. "Blocks.txt"). */
    public val fileName: String

    /** The Unicode property names this file carries. */
    public val propertyNames: List<String>

    /**
     * Compile: read text lines and populate the [MutablePuaaTable].
     *
     * @param table  the mutable table to add subtable entries to.
     * @param lines  the text lines of the property file (no trailing newlines).
     */
    public fun compile(table: MutablePuaaTable, lines: Sequence<String>)

    /**
     * Decompile: write the relevant properties from [table] as text lines.
     *
     * @param table  the PUAA table to read from.
     * @return the text lines of the property file.
     */
    public fun decompile(table: PuaaTable): List<String>

    override fun compareTo(other: PuaaCodec): Int = fileName.compareTo(other.fileName)
}
