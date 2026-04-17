package com.kreative.bitsnpicas.core.truetype

/**
 * Fallback table that holds raw bytes for any table tag
 * not yet recognized by the dispatch map in [TrueTypeFile].
 *
 * Compiles and decompiles losslessly: the raw bytes are stored
 * as-is and round-trip without interpretation.
 */
public class UnknownTable(
    override val tableName: String,
    public var data: ByteArray = ByteArray(0),
) : TrueTypeTable() {

    /** Construct from a 32-bit table ID. */
    public constructor(tableId: Int, data: ByteArray = ByteArray(0)) : this(idToTag(tableId), data)

    override fun compile(dependencies: Map<Int, TrueTypeTable>): ByteArray = data

    override fun decompile(data: ByteArray, dependencies: Map<Int, TrueTypeTable>) {
        this.data = data
    }
}
