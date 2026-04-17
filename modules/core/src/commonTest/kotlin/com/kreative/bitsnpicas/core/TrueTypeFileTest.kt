package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.truetype.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TrueTypeFileTest {

    /** Build a minimal TTF with head + name + post tables. */
    private fun buildMinimalTtf(): TrueTypeFile {
        val file = TrueTypeFile()

        val head = HeadTable()
        head.unitsPerEm = 1000
        head.flags = 0x000B
        head.xMin = -100
        head.yMin = -200
        head.xMax = 800
        head.yMax = 900
        head.dateCreated = 3_600_000_000L
        head.dateModified = 3_700_000_000L

        val name = NameTable()
        name.entries.add(NameTableEntry.forWindows(NameTableEntry.NAME_ID_FONT_FAMILY, "TestFont"))
        name.entries.add(NameTableEntry.forWindows(NameTableEntry.NAME_ID_FONT_SUBFAMILY, "Regular"))
        name.entries.add(NameTableEntry.forWindows(NameTableEntry.NAME_ID_FULL_NAME, "TestFont Regular"))

        val post = PostTable()
        post.format = PostTable.FORMAT_2
        post.entries.add(PostTableEntry(0))          // .notdef
        post.entries.add(PostTableEntry(3))           // space
        post.entries.add(PostTableEntry("myGlyph"))   // custom

        file.tables.add(head)
        file.tables.add(name)
        file.tables.add(post)

        return file
    }

    @Test
    fun compileAndDecompileRoundTrip() {
        val file1 = buildMinimalTtf()
        val bytes = file1.compile()

        // Check the TTF magic is present
        assertTrue(bytes.size > 12, "compiled file should be > 12 bytes")

        val file2 = TrueTypeFile()
        file2.decompile(bytes)

        assertEquals(TrueTypeFile.SCALER_TRUETYPE, file2.scaler)
        assertEquals(3, file2.tables.size, "should have 3 tables")

        // Verify head table round-trips
        val head2 = file2.getByTableName("head")
        assertNotNull(head2, "head table should be present")
        assertIs<HeadTable>(head2)
        assertEquals(1000, head2.unitsPerEm)
        assertEquals(0x000B, head2.flags)
        assertEquals(-100, head2.xMin)
        assertEquals(-200, head2.yMin)
        assertEquals(800, head2.xMax)
        assertEquals(900, head2.yMax)
        assertEquals(3_600_000_000L, head2.dateCreated)
        assertEquals(3_700_000_000L, head2.dateModified)

        // Verify name table round-trips
        val name2 = file2.getByTableName("name")
        assertNotNull(name2, "name table should be present")
        assertIs<NameTable>(name2)
        assertEquals(3, name2.entries.size)
        assertEquals("TestFont", name2.entries[0].getNameString())
        assertEquals("Regular", name2.entries[1].getNameString())
        assertEquals("TestFont Regular", name2.entries[2].getNameString())

        // Verify post table round-trips
        val post2 = file2.getByTableName("post")
        assertNotNull(post2, "post table should be present")
        assertIs<PostTable>(post2)
        assertEquals(PostTable.FORMAT_2, post2.format)
        assertEquals(3, post2.entries.size)
        assertEquals(0, post2.entries[0].intValue())
        assertEquals(3, post2.entries[1].intValue())
        assertEquals("myGlyph", post2.entries[2].stringValue())
    }

    @Test
    fun unknownTableRoundTrips() {
        val file1 = TrueTypeFile()

        val head = HeadTable()
        head.unitsPerEm = 1000
        file1.tables.add(head)

        // Add a fake unknown table
        val unknown = UnknownTable("XYZA", byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        file1.tables.add(unknown)

        val bytes = file1.compile()
        val file2 = TrueTypeFile()
        file2.decompile(bytes)

        assertEquals(2, file2.tables.size)

        val unknown2 = file2.getByTableName("XYZA")
        assertNotNull(unknown2, "unknown table should be present")
        assertIs<UnknownTable>(unknown2)
        assertEquals(listOf<Byte>(1, 2, 3, 4, 5, 6, 7, 8), unknown2.data.toList())
    }

    @Test
    fun doubleRoundTrip() {
        val file1 = buildMinimalTtf()
        val bytes1 = file1.compile()

        val file2 = TrueTypeFile()
        file2.decompile(bytes1)
        val bytes2 = file2.compile()

        val file3 = TrueTypeFile()
        file3.decompile(bytes2)

        // Verify the double round-trip produces same structured data
        val head3 = file3.getByTableName("head") as HeadTable
        assertEquals(1000, head3.unitsPerEm)
        assertEquals(-100, head3.xMin)

        val name3 = file3.getByTableName("name") as NameTable
        assertEquals(3, name3.entries.size)
        assertEquals("TestFont", name3.entries[0].getNameString())

        val post3 = file3.getByTableName("post") as PostTable
        assertEquals(3, post3.entries.size)
        assertEquals("myGlyph", post3.entries[2].stringValue())
    }

    @Test
    fun lookupByTableId() {
        val file = buildMinimalTtf()
        val bytes = file.compile()
        val file2 = TrueTypeFile()
        file2.decompile(bytes)

        assertNotNull(file2.getByTableId(0x68656164), "head by ID")
        assertNotNull(file2.getByTableId(0x6E616D65), "name by ID")
        assertNotNull(file2.getByTableId(0x706F7374), "post by ID")
    }
}
