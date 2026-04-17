package com.kreative.bitsnpicas.core.truetype

import com.kreative.bitsnpicas.core.ByteReader
import com.kreative.bitsnpicas.core.ByteWriter

/**
 * Base class for all TrueType/OpenType table types.
 *
 * Each concrete subclass knows how to [compile] its structured fields
 * into raw bytes and [decompile] raw bytes back into fields.
 *
 * The [tableName] is the 4-character ASCII tag (e.g. "head", "name").
 * The [tableId] is the big-endian 32-bit integer encoding of that tag.
 */
public abstract class TrueTypeTable {
    /** 4-character ASCII tag for this table (e.g. "head", "name", "post"). */
    public abstract val tableName: String

    /** Big-endian 32-bit encoding of the 4-char [tableName]. */
    public val tableId: Int
        get() {
            val n = tableName
            fun ch(i: Int): Int =
                if (i < n.length && n[i].code in 0x20 until 0x7F) n[i].code else 0x20
            return (ch(0) shl 24) or (ch(1) shl 16) or (ch(2) shl 8) or ch(3)
        }

    /**
     * Compile this table's structured fields into a byte array.
     * The [dependencies] map provides other tables this one needs
     * to reference (keyed by table ID).
     */
    public abstract fun compile(dependencies: Map<Int, TrueTypeTable> = emptyMap()): ByteArray

    /**
     * Decompile raw table bytes into this table's structured fields.
     * The [dependencies] map provides already-decompiled tables.
     */
    public abstract fun decompile(data: ByteArray, dependencies: Map<Int, TrueTypeTable> = emptyMap())

    /**
     * Table IDs this table depends on for compile/decompile.
     * Override in subclasses that reference other tables.
     * By default, a table has no dependencies.
     */
    public open val dependencyIds: List<Int> get() = emptyList()

    public companion object {
        /** Convert a 4-char ASCII tag string to its 32-bit table ID. */
        public fun tagToId(tag: String): Int {
            fun ch(i: Int): Int =
                if (i < tag.length && tag[i].code in 0x20 until 0x7F) tag[i].code else 0x20
            return (ch(0) shl 24) or (ch(1) shl 16) or (ch(2) shl 8) or ch(3)
        }

        /** Convert a 32-bit table ID to a 4-char ASCII tag string. */
        public fun idToTag(id: Int): String = buildString {
            append(((id shr 24) and 0xFF).toChar())
            append(((id shr 16) and 0xFF).toChar())
            append(((id shr 8) and 0xFF).toChar())
            append((id and 0xFF).toChar())
        }
    }
}
