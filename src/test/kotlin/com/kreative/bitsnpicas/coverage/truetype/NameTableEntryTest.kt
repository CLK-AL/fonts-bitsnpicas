package com.kreative.bitsnpicas.coverage.truetype

import com.kreative.bitsnpicas.truetype.NameTableEntry
import com.kreative.bitsnpicas.truetype.PlatformConstants
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cover NameTableEntry's get/set encoder branches: Unicode (UTF-16BE),
 * Macintosh Roman, ISO 10646, Windows Unicode-16, Windows Unicode-32, and
 * the IllegalStateException branches for unrecognised platform/specific
 * combinations. Also exercise compareTo, factory methods and field equality.
 */
class NameTableEntryTest {

    @Test
    fun forUnicode_setNameString_uses_utf16be() {
        val e = NameTableEntry.forUnicode(NameTableEntry.NAME_ID_FONT_FAMILY, "Tiny")
        assertEquals(PlatformConstants.PLATFORM_ID_UNICODE, e.platformID)
        // "Tiny" in UTF-16BE is 8 bytes.
        assertEquals(8, e.nameData.size)
        assertEquals("Tiny", e.nameString)
    }

    @Test
    fun forMacintosh_round_trip() {
        val e = NameTableEntry.forMacintosh(NameTableEntry.NAME_ID_COPYRIGHT_NOTICE, "Public Domain")
        assertEquals(PlatformConstants.PLATFORM_ID_MACINTOSH, e.platformID)
        assertEquals("Public Domain", e.nameString)
    }

    @Test
    fun forWindows_uses_utf16be() {
        val e = NameTableEntry.forWindows(NameTableEntry.NAME_ID_VERSION, "1.0")
        assertEquals(PlatformConstants.PLATFORM_ID_WINDOWS, e.platformID)
        assertEquals(PlatformConstants.PLATFORM_SPECIFIC_ID_WINDOWS_UNICODE_16, e.platformSpecificID)
        assertEquals(6, e.nameData.size) // 3 chars * 2
        assertEquals("1.0", e.nameString)
    }

    @Test
    fun iso10646_round_trip() {
        val e = NameTableEntry()
        e.platformID = PlatformConstants.PLATFORM_ID_ISO_10646
        e.platformSpecificID = 0
        e.languageID = 0
        e.nameID = NameTableEntry.NAME_ID_DESCRIPTION
        e.setNameString("Hello")
        assertEquals("Hello", e.nameString)
    }

    @Test
    fun windowsUnicode32_round_trip() {
        val e = NameTableEntry()
        e.platformID = PlatformConstants.PLATFORM_ID_WINDOWS_UNICODE
        e.platformSpecificID = PlatformConstants.PLATFORM_SPECIFIC_ID_WINDOWS_UNICODE_32
        e.languageID = 0
        e.nameID = 1
        e.setNameString("ABC")
        assertEquals(12, e.nameData.size) // 3 chars * 4
        assertEquals("ABC", e.nameString)
    }

    @Test
    fun unsupported_platform_throws_for_setNameString() {
        val e = NameTableEntry()
        e.platformID = 0xDEAD
        assertThrows<IllegalStateException> { e.setNameString("x") }
        e.nameData = byteArrayOf(1, 2)
        assertThrows<IllegalStateException> { e.nameString }
    }

    @Test
    fun unsupported_macintosh_specific_throws() {
        val e = NameTableEntry()
        e.platformID = PlatformConstants.PLATFORM_ID_MACINTOSH
        e.platformSpecificID = 999 // unknown
        assertThrows<IllegalStateException> { e.setNameString("x") }
        e.nameData = byteArrayOf(1, 2)
        assertThrows<IllegalStateException> { e.nameString }
    }

    @Test
    fun unsupported_windows_specific_throws() {
        val e = NameTableEntry()
        e.platformID = PlatformConstants.PLATFORM_ID_WINDOWS
        e.platformSpecificID = 999
        assertThrows<IllegalStateException> { e.setNameString("x") }
        e.nameData = byteArrayOf(1, 2)
        assertThrows<IllegalStateException> { e.nameString }
    }

    @Test
    fun compareTo_orders_by_index_then_platform_then_language_then_nameId() {
        val a = NameTableEntry().apply { index = 0; platformID = 0; platformSpecificID = 0; languageID = 0; nameID = 1 }
        val b = NameTableEntry().apply { index = 0; platformID = 0; platformSpecificID = 0; languageID = 0; nameID = 2 }
        assertTrue(a.compareTo(b) < 0)
        assertEquals(0, a.compareTo(NameTableEntry().apply {
            index = 0; platformID = 0; platformSpecificID = 0; languageID = 0; nameID = 1
        }))
        // Index difference dominates.
        val c = NameTableEntry().apply { index = 5; platformID = 0; platformSpecificID = 0; languageID = 0; nameID = 0 }
        assertTrue(a.compareTo(c) < 0)
        // Platform differences.
        val d = NameTableEntry().apply { index = 0; platformID = 9; platformSpecificID = 0; languageID = 0; nameID = 0 }
        assertTrue(a.compareTo(d) < 0)
        // Specific differences.
        val e = NameTableEntry().apply { index = 0; platformID = 0; platformSpecificID = 9; languageID = 0; nameID = 0 }
        assertTrue(a.compareTo(e) < 0)
        // Language differences.
        val f = NameTableEntry().apply { index = 0; platformID = 0; platformSpecificID = 0; languageID = 9; nameID = 0 }
        assertTrue(a.compareTo(f) < 0)
    }
}
