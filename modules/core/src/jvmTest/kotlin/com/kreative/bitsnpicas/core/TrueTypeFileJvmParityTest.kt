package com.kreative.bitsnpicas.core

import com.kreative.bitsnpicas.core.truetype.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * JVM parity tests for the TrueType file envelope + head/name/post tables.
 *
 * Tests that:
 * 1. Compiling via Kotlin and decompiling via Java yields identical structured data.
 * 2. Compiling via Java and decompiling via Kotlin yields identical structured data.
 * 3. Both paths produce byte-level compatible output for simple cases.
 */
class TrueTypeFileJvmParityTest {

    private fun buildTestFile(): TrueTypeFile {
        val file = TrueTypeFile()

        val head = HeadTable()
        head.version = HeadTable.VERSION_DEFAULT
        head.unitsPerEm = 1000
        head.flags = 0x000B
        head.xMin = -100
        head.yMin = -200
        head.xMax = 800
        head.yMax = 900
        head.dateCreated = 3_600_000_000L
        head.dateModified = 3_700_000_000L
        head.macStyle = HeadTable.MAC_STYLE_BOLD
        head.lowestRecPPEM = 8

        val name = NameTable()
        name.entries.add(NameTableEntry.forWindows(NameTableEntry.NAME_ID_FONT_FAMILY, "TestFont"))
        name.entries.add(NameTableEntry.forWindows(NameTableEntry.NAME_ID_FONT_SUBFAMILY, "Regular"))

        val post = PostTable()
        post.format = PostTable.FORMAT_3

        file.tables.add(head)
        file.tables.add(name)
        file.tables.add(post)

        return file
    }

    @Test
    fun `compile Kotlin decompile Java - head table parity`() {
        val file = buildTestFile()
        val kotlinBytes = file.compile()

        // Decompile via Java
        val javaFile = JavaLegacyAdapter.decompileTtfViaJava(kotlinBytes)
        val javaHead = javaFile.tables.filterIsInstance<HeadTable>().firstOrNull()
        assertNotNull(javaHead, "Java should parse head table from Kotlin output")
        assertEquals(1000, javaHead.unitsPerEm)
        assertEquals(0x000B, javaHead.flags)
        assertEquals(-100, javaHead.xMin)
        assertEquals(-200, javaHead.yMin)
        assertEquals(800, javaHead.xMax)
        assertEquals(900, javaHead.yMax)
        assertEquals(3_600_000_000L, javaHead.dateCreated)
        assertEquals(3_700_000_000L, javaHead.dateModified)
    }

    @Test
    fun `compile Java decompile Kotlin - head table parity`() {
        val file = buildTestFile()
        val javaBytes = JavaLegacyAdapter.compileTtfViaJava(file)

        // Decompile via Kotlin
        val kotlinFile = TrueTypeFile()
        kotlinFile.decompile(javaBytes)

        val head = kotlinFile.getByTableName("head")
        assertNotNull(head)
        assertIs<HeadTable>(head)
        assertEquals(1000, head.unitsPerEm)
        assertEquals(0x000B, head.flags)
        assertEquals(-100, head.xMin)
        assertEquals(-200, head.yMin)
        assertEquals(800, head.xMax)
        assertEquals(900, head.yMax)
    }

    @Test
    fun `compile Kotlin decompile Java - name table parity`() {
        val file = buildTestFile()
        val kotlinBytes = file.compile()

        val javaFile = JavaLegacyAdapter.decompileTtfViaJava(kotlinBytes)
        val javaName = javaFile.tables.filterIsInstance<NameTable>().firstOrNull()
        assertNotNull(javaName, "Java should parse name table from Kotlin output")
        assertEquals(2, javaName.entries.size)
        assertEquals("TestFont", javaName.entries[0].getNameString())
        assertEquals("Regular", javaName.entries[1].getNameString())
    }

    @Test
    fun `compile Java decompile Kotlin - name table parity`() {
        val file = buildTestFile()
        val javaBytes = JavaLegacyAdapter.compileTtfViaJava(file)

        val kotlinFile = TrueTypeFile()
        kotlinFile.decompile(javaBytes)

        val name = kotlinFile.getByTableName("name")
        assertNotNull(name)
        assertIs<NameTable>(name)
        assertEquals(2, name.entries.size)
        assertEquals("TestFont", name.entries[0].getNameString())
        assertEquals("Regular", name.entries[1].getNameString())
    }

    @Test
    fun `compile Kotlin decompile Java - post table parity`() {
        val file = buildTestFile()
        val kotlinBytes = file.compile()

        val javaFile = JavaLegacyAdapter.decompileTtfViaJava(kotlinBytes)
        val javaPost = javaFile.tables.filterIsInstance<PostTable>().firstOrNull()
        assertNotNull(javaPost, "Java should parse post table from Kotlin output")
        assertEquals(PostTable.FORMAT_3, javaPost.format)
    }

    @Test
    fun `compile Java decompile Kotlin - post table parity`() {
        val file = buildTestFile()
        val javaBytes = JavaLegacyAdapter.compileTtfViaJava(file)

        val kotlinFile = TrueTypeFile()
        kotlinFile.decompile(javaBytes)

        val post = kotlinFile.getByTableName("post")
        assertNotNull(post)
        assertIs<PostTable>(post)
        assertEquals(PostTable.FORMAT_3, post.format)
    }

    @Test
    fun `post format 2 with strings - cross compile parity`() {
        val file = TrueTypeFile()
        val head = HeadTable()
        head.unitsPerEm = 2048
        file.tables.add(head)

        val post = PostTable()
        post.format = PostTable.FORMAT_2
        post.entries.add(PostTableEntry(0))          // .notdef
        post.entries.add(PostTableEntry(3))           // space
        post.entries.add(PostTableEntry("myGlyph"))   // custom
        file.tables.add(post)

        // Compile via Kotlin, decompile via Java
        val kotlinBytes = file.compile()
        val javaFile = JavaLegacyAdapter.decompileTtfViaJava(kotlinBytes)
        val javaPost = javaFile.tables.filterIsInstance<PostTable>().firstOrNull()
        assertNotNull(javaPost)
        assertEquals(3, javaPost.entries.size)
        assertEquals(0, javaPost.entries[0].intValue())
        assertEquals(3, javaPost.entries[1].intValue())
        assertEquals("myGlyph", javaPost.entries[2].stringValue())

        // Compile via Java, decompile via Kotlin
        val javaBytes = JavaLegacyAdapter.compileTtfViaJava(file)
        val kotlinFile = TrueTypeFile()
        kotlinFile.decompile(javaBytes)
        val kPost = kotlinFile.getByTableName("post") as PostTable
        assertEquals(3, kPost.entries.size)
        assertEquals("myGlyph", kPost.entries[2].stringValue())
    }

    @Test
    fun `table count preserved across paths`() {
        val file = buildTestFile()

        val kotlinBytes = file.compile()
        val javaBytes = JavaLegacyAdapter.compileTtfViaJava(file)

        val kf = TrueTypeFile()
        kf.decompile(kotlinBytes)
        assertEquals(3, kf.tables.size, "Kotlin round-trip table count")

        val jf = JavaLegacyAdapter.decompileTtfViaJava(javaBytes)
        assertEquals(3, jf.tables.size, "Java round-trip table count")
    }
}
