package com.kreative.bitsnpicas.core.puaa

/**
 * Sealed hierarchy representing the 9 entry variants of a PUAA subtable.
 *
 * Each entry maps a code-point range (`firstCodePoint..lastCodePoint`)
 * to a typed value. The type IDs match the frozen Java
 * `PuaaSubtableEntry` constants exactly.
 */
public sealed class PuaaEntry {
    public abstract val firstCodePoint: Int
    public abstract val lastCodePoint: Int

    /** Returns the string property value for the given code point. */
    public abstract fun getPropertyValue(cp: Int): String?

    public companion object {
        public const val TYPE_SINGLE: Int = 1
        public const val TYPE_MULTIPLE: Int = 2
        public const val TYPE_BOOLEAN: Int = 3
        public const val TYPE_DECIMAL: Int = 4
        public const val TYPE_HEXADECIMAL: Int = 5
        public const val TYPE_HEXMULTIPLE: Int = 6
        public const val TYPE_HEXSEQUENCE: Int = 7
        public const val TYPE_CASEMAPPING: Int = 8
        public const val TYPE_NAMEALIAS: Int = 9
    }

    /** A single string value shared across a code-point range. */
    public data class Single(
        override val firstCodePoint: Int,
        override val lastCodePoint: Int,
        val value: String?,
    ) : PuaaEntry() {
        override fun getPropertyValue(cp: Int): String? = value
    }

    /** Per-code-point string values (one per code point in the range). */
    public data class Multiple(
        override val firstCodePoint: Int,
        override val lastCodePoint: Int,
        val values: List<String?>,
    ) : PuaaEntry() {
        override fun getPropertyValue(cp: Int): String? {
            val idx = cp - firstCodePoint
            return if (idx in values.indices) values[idx] else null
        }
    }

    /** A boolean value shared across a code-point range. */
    public data class BooleanEntry(
        override val firstCodePoint: Int,
        override val lastCodePoint: Int,
        val value: Boolean,
    ) : PuaaEntry() {
        override fun getPropertyValue(cp: Int): String = if (value) "Y" else "N"
    }

    /** A signed decimal integer shared across a code-point range. */
    public data class Decimal(
        override val firstCodePoint: Int,
        override val lastCodePoint: Int,
        val value: Int,
    ) : PuaaEntry() {
        override fun getPropertyValue(cp: Int): String = value.toString()
    }

    /** A hex integer shared across a code-point range. */
    public data class Hexadecimal(
        override val firstCodePoint: Int,
        override val lastCodePoint: Int,
        val value: Int,
    ) : PuaaEntry() {
        override fun getPropertyValue(cp: Int): String = toHex4(value)
    }

    /** Per-code-point hex integer values. */
    public data class HexMultiple(
        override val firstCodePoint: Int,
        override val lastCodePoint: Int,
        val values: IntArray,
    ) : PuaaEntry() {
        override fun getPropertyValue(cp: Int): String? {
            val idx = cp - firstCodePoint
            return if (idx in values.indices) toHex4(values[idx]) else null
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is HexMultiple) return false
            return firstCodePoint == other.firstCodePoint &&
                lastCodePoint == other.lastCodePoint &&
                values.contentEquals(other.values)
        }

        override fun hashCode(): Int {
            var result = firstCodePoint
            result = 31 * result + lastCodePoint
            result = 31 * result + values.contentHashCode()
            return result
        }
    }

    /** A fixed sequence of hex integers shared across a code-point range. */
    public data class HexSequence(
        override val firstCodePoint: Int,
        override val lastCodePoint: Int,
        val values: IntArray,
    ) : PuaaEntry() {
        override fun getPropertyValue(cp: Int): String {
            return values.joinToString(" ") { toHex4(it) }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is HexSequence) return false
            return firstCodePoint == other.firstCodePoint &&
                lastCodePoint == other.lastCodePoint &&
                values.contentEquals(other.values)
        }

        override fun hashCode(): Int {
            var result = firstCodePoint
            result = 31 * result + lastCodePoint
            result = 31 * result + values.contentHashCode()
            return result
        }
    }

    /** Case mapping: a sequence of hex code points + optional condition string. */
    public data class CaseMapping(
        override val firstCodePoint: Int,
        override val lastCodePoint: Int,
        val values: IntArray,
        val condition: String?,
    ) : PuaaEntry() {
        override fun getPropertyValue(cp: Int): String {
            val sb = StringBuilder()
            for (i in values.indices) {
                if (i > 0) sb.append(' ')
                sb.append(toHex4(values[i]))
            }
            if (!condition.isNullOrEmpty()) {
                sb.append("; ")
                sb.append(condition)
            }
            return sb.toString()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CaseMapping) return false
            return firstCodePoint == other.firstCodePoint &&
                lastCodePoint == other.lastCodePoint &&
                values.contentEquals(other.values) &&
                condition == other.condition
        }

        override fun hashCode(): Int {
            var result = firstCodePoint
            result = 31 * result + lastCodePoint
            result = 31 * result + values.contentHashCode()
            result = 31 * result + (condition?.hashCode() ?: 0)
            return result
        }
    }

    /** Name alias: alias string + type string. */
    public data class NameAlias(
        override val firstCodePoint: Int,
        override val lastCodePoint: Int,
        val alias: String?,
        val type: String?,
    ) : PuaaEntry() {
        override fun getPropertyValue(cp: Int): String? {
            if (alias == null || type == null) return null
            return "$alias;$type"
        }
    }
}

/** Format an int as uppercase hex, padded to at least 4 digits. */
internal fun toHex4(value: Int): String {
    val s = value.toUInt().toString(16).uppercase()
    return if (s.length >= 4) s else s.padStart(4, '0')
}
