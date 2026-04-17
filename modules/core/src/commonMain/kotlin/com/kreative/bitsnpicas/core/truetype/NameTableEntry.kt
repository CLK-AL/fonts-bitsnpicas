package com.kreative.bitsnpicas.core.truetype

/**
 * A single entry in the `name` table. Each entry contains a name
 * string encoded as raw bytes (the encoding depends on the platform).
 *
 * Platform/encoding constants are inlined here rather than pulling
 * in a separate PlatformConstants file, to keep the port minimal.
 */
public class NameTableEntry : Comparable<NameTableEntry> {
    public var index: Int = 0
    public var platformID: Int = 0
    public var platformSpecificID: Int = 0
    public var languageID: Int = 0
    public var nameID: Int = 0
    public var nameData: ByteArray = ByteArray(0)
    public var padding: Int = 0

    override fun compareTo(other: NameTableEntry): Int {
        if (this.index != other.index) return this.index - other.index
        if (this.platformID != other.platformID) return this.platformID - other.platformID
        if (this.platformSpecificID != other.platformSpecificID) return this.platformSpecificID - other.platformSpecificID
        if (this.languageID != other.languageID) return this.languageID - other.languageID
        if (this.nameID != other.nameID) return this.nameID - other.nameID
        return 0
    }

    /**
     * Get the name string, decoding based on the platform.
     * For Unicode/Windows platform IDs, decodes as UTF-16BE.
     * For Macintosh Roman, decodes as ASCII (lossy for non-ASCII).
     */
    public fun getNameString(): String {
        return when (platformID) {
            PLATFORM_ID_UNICODE, PLATFORM_ID_ISO_10646 -> decodeUtf16BE(nameData)
            PLATFORM_ID_MACINTOSH -> {
                // MacRoman: approximate with ASCII for commonMain (no charset support)
                buildString {
                    for (b in nameData) {
                        append((b.toInt() and 0xFF).toChar())
                    }
                }
            }
            PLATFORM_ID_WINDOWS, PLATFORM_ID_WINDOWS_UNICODE -> {
                when (platformSpecificID) {
                    PLATFORM_SPECIFIC_ID_WINDOWS_UNICODE_16 -> decodeUtf16BE(nameData)
                    else -> decodeUtf16BE(nameData) // best effort
                }
            }
            else -> decodeUtf16BE(nameData) // best effort
        }
    }

    /**
     * Set the name string, encoding based on the platform.
     */
    public fun setNameString(name: String) {
        nameData = when (platformID) {
            PLATFORM_ID_UNICODE, PLATFORM_ID_ISO_10646 -> encodeUtf16BE(name)
            PLATFORM_ID_MACINTOSH -> {
                // MacRoman: approximate with low byte of each char
                ByteArray(name.length) { i -> (name[i].code and 0xFF).toByte() }
            }
            PLATFORM_ID_WINDOWS, PLATFORM_ID_WINDOWS_UNICODE -> encodeUtf16BE(name)
            else -> encodeUtf16BE(name)
        }
    }

    public companion object {
        public const val PLATFORM_ID_UNICODE: Int = 0
        public const val PLATFORM_ID_MACINTOSH: Int = 1
        public const val PLATFORM_ID_ISO_10646: Int = 2
        public const val PLATFORM_ID_WINDOWS: Int = 3
        public const val PLATFORM_ID_WINDOWS_UNICODE: Int = 10

        public const val PLATFORM_SPECIFIC_ID_UNICODE_2_0: Int = 3
        public const val PLATFORM_SPECIFIC_ID_MACINTOSH_ROMAN: Int = 0
        public const val PLATFORM_SPECIFIC_ID_WINDOWS_UNICODE_16: Int = 1

        public const val LANGUAGE_ID_MACINTOSH_ENGLISH: Int = 0
        public const val LANGUAGE_ID_WINDOWS_ENGLISH: Int = 1033

        public const val NAME_ID_COPYRIGHT_NOTICE: Int = 0
        public const val NAME_ID_FONT_FAMILY: Int = 1
        public const val NAME_ID_FONT_SUBFAMILY: Int = 2
        public const val NAME_ID_UNIQUE_SUBFAMILY_ID: Int = 3
        public const val NAME_ID_FULL_NAME: Int = 4
        public const val NAME_ID_VERSION: Int = 5
        public const val NAME_ID_POSTSCRIPT_NAME: Int = 6

        /** Create a Unicode platform name entry. */
        public fun forUnicode(nameID: Int, name: String): NameTableEntry {
            val e = NameTableEntry()
            e.platformID = PLATFORM_ID_UNICODE
            e.platformSpecificID = PLATFORM_SPECIFIC_ID_UNICODE_2_0
            e.languageID = 0
            e.nameID = nameID
            e.setNameString(name)
            return e
        }

        /** Create a Windows platform name entry. */
        public fun forWindows(nameID: Int, name: String): NameTableEntry {
            val e = NameTableEntry()
            e.platformID = PLATFORM_ID_WINDOWS
            e.platformSpecificID = PLATFORM_SPECIFIC_ID_WINDOWS_UNICODE_16
            e.languageID = LANGUAGE_ID_WINDOWS_ENGLISH
            e.nameID = nameID
            e.setNameString(name)
            return e
        }

        /** Create a Macintosh platform name entry. */
        public fun forMacintosh(nameID: Int, name: String): NameTableEntry {
            val e = NameTableEntry()
            e.platformID = PLATFORM_ID_MACINTOSH
            e.platformSpecificID = PLATFORM_SPECIFIC_ID_MACINTOSH_ROMAN
            e.languageID = LANGUAGE_ID_MACINTOSH_ENGLISH
            e.nameID = nameID
            e.setNameString(name)
            return e
        }

        private fun decodeUtf16BE(data: ByteArray): String {
            val sb = StringBuilder(data.size / 2)
            var i = 0
            while (i + 1 < data.size) {
                val ch = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
                sb.append(ch.toChar())
                i += 2
            }
            return sb.toString()
        }

        private fun encodeUtf16BE(s: String): ByteArray {
            val result = ByteArray(s.length * 2)
            for (i in s.indices) {
                val ch = s[i].code
                result[i * 2] = ((ch shr 8) and 0xFF).toByte()
                result[i * 2 + 1] = (ch and 0xFF).toByte()
            }
            return result
        }
    }
}
