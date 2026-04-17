package com.kreative.bitsnpicas.core.truetype

/**
 * A single entry in the `post` table. Each entry is either an
 * integer index (into the standard Mac glyph name list) or a
 * custom string name.
 */
public class PostTableEntry private constructor(
    private val intValue: Int?,
    private val stringValue: String?,
) {
    public constructor(intValue: Int) : this(intValue, null)
    public constructor(stringValue: String) : this(null, stringValue)

    public fun isInteger(): Boolean = intValue != null
    public fun isString(): Boolean = stringValue != null
    public fun intValue(): Int = intValue ?: 0
    public fun stringValue(): String = stringValue ?: ""

    override fun toString(): String = when {
        intValue != null -> intValue.toString()
        stringValue != null -> stringValue
        else -> ""
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PostTableEntry) return false
        return intValue == other.intValue && stringValue == other.stringValue
    }

    override fun hashCode(): Int = intValue?.hashCode() ?: stringValue?.hashCode() ?: 0
}
