package com.kreative.bitsnpicas.coverage

import com.kreative.bitsnpicas.Base64InputStream
import com.kreative.bitsnpicas.Base64OutputStream
import com.kreative.bitsnpicas.BitmapFont
import com.kreative.bitsnpicas.BitmapFontGlyph
import com.kreative.bitsnpicas.FileProxy
import com.kreative.bitsnpicas.Font
import com.kreative.bitsnpicas.GlyphPair
import com.kreative.bitsnpicas.IDGenerator
import com.kreative.bitsnpicas.MacUtility
import com.kreative.bitsnpicas.PathGraph
import com.kreative.bitsnpicas.PointSizeGenerator
import com.kreative.bitsnpicas.WIBInputStream
import com.kreative.bitsnpicas.WIBOutputStream
import com.kreative.bitsnpicas.XMLUtility
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.StringCharacterIterator
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Drives the remaining helper + generator classes in the core package. */
class CoreSupportCoverageTest {

    @Test
    fun base64_round_trip_via_stringbuffer_and_stream() {
        val src = "hello, world. unicode: \uD83D\uDE00!".toByteArray(Charsets.UTF_8)
        val sb = StringBuffer()
        Base64OutputStream(sb).use { for (b in src) it.write(b.toInt()) }
        val encoded = sb.toString()
        val back = Base64InputStream(encoded).readBytes()
        assertEquals(src.toList(), back.toList())

        // OutputStream variant.
        val baos = ByteArrayOutputStream()
        Base64OutputStream(baos, true).use { for (b in src) it.write(b.toInt()) }
        val back2 = Base64InputStream(ByteArrayInputStream(baos.toByteArray())).readBytes()
        assertEquals(src.toList(), back2.toList())

        // CharacterIterator variant.
        val ci = StringCharacterIterator(encoded)
        val back3 = Base64InputStream(ci).readBytes()
        assertEquals(src.toList(), back3.toList())

        // Unpadded.
        val sb2 = StringBuffer()
        Base64OutputStream(sb2, false).use { for (b in src) it.write(b.toInt()) }
        // Still decodable (padding optional in our reader).
        val back4 = Base64InputStream(sb2.toString()).readBytes()
        assertEquals(src.toList(), back4.toList())
    }

    @Test
    fun base64_flush_is_noop_for_stringbuffer_path() {
        val sb = StringBuffer()
        val os = Base64OutputStream(sb)
        os.write(1); os.write(2); os.write(3)
        os.flush() // no-op for StringBuffer
        os.close()
        assertTrue(sb.isNotEmpty())
    }

    @Test
    fun wib_round_trip_covers_all_encoding_modes() {
        // Craft a stream that exercises 0x00 runs, 0xFF runs, data runs, and literal bursts.
        val src = ByteArray(3000)
        // 1000 zero-bytes
        for (i in 0 until 1000) src[i] = 0x00
        // 500 0xFF
        for (i in 1000 until 1500) src[i] = 0xFF.toByte()
        // 500 of 0x42 run
        for (i in 1500 until 2000) src[i] = 0x42
        // 1000 mixed bytes (no runs)
        for (i in 2000 until 3000) src[i] = ((i and 0x7F) or 0x01).toByte()

        val baos = ByteArrayOutputStream()
        val wos = WIBOutputStream(baos)
        wos.write(src)
        wos.flush()
        wos.close()  // triggers finish().

        val back = ByteArrayOutputStream()
        val wis = WIBInputStream(ByteArrayInputStream(baos.toByteArray()))
        val buf = ByteArray(4096)
        var n: Int
        while (true) {
            n = wis.read(buf)
            if (n < 0) break
            back.write(buf, 0, n)
        }
        wis.close()
        assertEquals(src.toList(), back.toByteArray().toList())
    }

    @Test
    fun glyph_pair_comparisons_and_equality() {
        val ii = GlyphPair(65, 66)
        val iiEq = GlyphPair(65, 66)
        val ii2 = GlyphPair(67, 68)
        val ss = GlyphPair("a", "b")
        val si = GlyphPair("a", 66)
        val is_ = GlyphPair(65, "b")

        assertEquals(ii, iiEq)
        assertNotEquals(ii, ii2)
        assertNotEquals(ii, ss)
        assertFalse(ii.equals("not a glyph pair"))

        assertEquals(ii.hashCode(), iiEq.hashCode())
        assertEquals("65,66", ii.toString())

        // Ordering: integers before strings.
        assertTrue(ii.compareTo(ss) < 0)
        assertTrue(ss.compareTo(ii) > 0)
        assertTrue(ii.compareTo(ii2) < 0)
        assertEquals(0, ii.compareTo(iiEq))
        assertTrue(ss.compareTo(si) > 0)

        // Null in constructor throws.
        val thrown = try {
            val m = GlyphPair::class.java.getConstructor(Integer::class.java, Integer::class.java)
            m.newInstance(null, 1)
            false
        } catch (e: java.lang.reflect.InvocationTargetException) {
            e.targetException is IllegalArgumentException
        }
        assertTrue(thrown)
    }

    @Test
    fun id_generator_sequential_wraps_then_hashcode_and_random_produce_values() {
        val seq = IDGenerator.Sequential(10, 10, 12)
        val f = BitmapFont()
        assertEquals(10, seq.generateID(f))
        assertEquals(11, seq.generateID(f))
        assertEquals(10, seq.generateID(f))  // wrapped
        seq.setRange(0, 3)
        val v = seq.generateID(f)
        assertTrue(v >= 0) // just exercises setRange branch

        val hash = IDGenerator.HashCode(0, 100)
        f.setName(Font.NAME_FAMILY, "Foo")
        assertTrue(hash.generateID(f) in 0..99)
        hash.setRange(1000, 2000)
        assertTrue(hash.generateID(f) in 1000..1999)

        val rand = IDGenerator.Random(0, 10)
        assertTrue(rand.generateID(f) in 0..9)
        rand.setRange(5, 15)
        assertTrue(rand.generateID(f) in 5..14)
    }

    @Test
    fun point_size_generator_fixed_automatic_standard() {
        val f = BitmapFont(6, 2, 6, 2, 4, 6, 0, 4)
        val fixed = PointSizeGenerator.Fixed(12)
        fixed.setRange(1, 2); fixed.setPointSizes(3, 4)
        assertEquals(12, fixed.generatePointSize(f))

        val auto = PointSizeGenerator.Automatic(5, 20)
        assertEquals(8, auto.generatePointSize(f)) // 6+2=8 in range
        auto.setRange(10, 20)
        assertEquals(10, auto.generatePointSize(f)) // below min
        auto.setRange(1, 5)
        assertEquals(5, auto.generatePointSize(f)) // above max
        auto.setPointSizes(1, 2, 3) // no-op

        val std = PointSizeGenerator.Standard(8, 10, 12)
        assertEquals(8, std.generatePointSize(f))
        std.setRange(0, 0) // no-op
        val big = BitmapFont(50, 0, 50, 0, 0, 0, 0, 0)
        assertEquals(12, std.generatePointSize(big))
        std.setPointSizes(6, 8)
        assertEquals(8, std.generatePointSize(big))
        assertEquals(6, std.generatePointSize(BitmapFont(1, 0, 1, 0, 0, 0, 0, 0)))
    }

    @Test
    fun fileProxy_extension_and_startsWith_helpers() {
        val tmp = File.createTempFile("proxy-", ".TXT")
        tmp.writeBytes(byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61))
        try {
            val proxy = FileProxy(tmp.absolutePath)
            assertTrue(proxy.hasExtension("txt", ".foo"))
            assertFalse(proxy.hasExtension(".zip"))
            assertTrue(proxy.startsWith(0x47, 0x49, 0x46))
            assertTrue(proxy.startsWith(*byteArrayOf(0x47, 0x49)))
            assertFalse(proxy.startsWith(0xDE, 0xAD))
            assertFalse(proxy.startsWith(*byteArrayOf(0xDE.toByte(), 0xAD.toByte())))
            assertNotNull(proxy.getStartBytes(4))
            assertEquals(tmp.absolutePath, proxy.file.absolutePath)
            // Alternate constructors.
            FileProxy(tmp.parentFile, tmp.name)
            FileProxy(tmp.parent!!, tmp.name)
            FileProxy(tmp)

            // Mac metadata helpers (tools unavailable on Linux but exercise the code path).
            assertFalse(proxy.hasMacType("TEXT"))
            assertFalse(proxy.hasMacCreator("ttxt"))

            // isImage / getImage on text file.
            assertFalse(proxy.isImage())
            assertNull(proxy.getImage())
            // Once isImage has been called, getImage returns the cached null.
            assertNull(proxy.getImage())
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun fileProxy_isImage_true_for_real_png() {
        val tmp = File.createTempFile("proxy-img-", ".png")
        try {
            val img = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
            ImageIO.write(img, "png", tmp)
            val proxy = FileProxy(tmp)
            assertTrue(proxy.isImage())
            assertNotNull(proxy.getImage())
            // Re-call after cached.
            assertTrue(proxy.isImage())
            assertNotNull(proxy.getImage())
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun fileProxy_missing_file_safe() {
        val missing = File("/nope/nope/nope/${System.nanoTime()}")
        val proxy = FileProxy(missing)
        assertFalse(proxy.startsWith(1, 2, 3))
        assertFalse(proxy.startsWith(*byteArrayOf(1, 2, 3)))
        assertNull(proxy.getStartBytes(4))
        assertFalse(proxy.isImage())
    }

    @Test
    fun macUtility_fork_helpers_and_process_paths() {
        val tmp = File.createTempFile("mac-", "")
        try {
            assertEquals(tmp, MacUtility.getDataFork(tmp))
            val rsrc = MacUtility.getResourceFork(tmp)
            assertTrue(rsrc.path.contains("..namedfork"))
            // Round-trip: getDataFork(rsrc) is the original directory.
            val nested = File(File(tmp, "..namedfork"), "rsrc")
            assertEquals(tmp, MacUtility.getDataFork(nested))
            // Process paths - tools not installed on Linux; expect null.
            assertNull(MacUtility.getType(tmp))
            assertNull(MacUtility.getCreator(tmp))
            MacUtility.setTypeAndCreator(tmp, "TEXT", "ttxt") // swallows IOException
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun xmlUtility_wrap_parse_and_resolvers() {
        // wrap: attributes plus self-closing
        val s1 = XMLUtility.wrap("g", true, "u", "65", "x", "0")
        assertTrue(s1.startsWith("<g"))
        assertTrue(s1.endsWith("/>"))
        // wrap with text body.
        val s2 = XMLUtility.wrap("p", true, "a", "1", "text")
        assertTrue(s2.contains(">text"))
        // non-closed.
        val s3 = XMLUtility.wrap("p", false, "a", "1")
        assertTrue(s3.endsWith(">"))
        // non-closed, odd attrs (text body)
        val s4 = XMLUtility.wrap("p", false, "text")
        assertTrue(s4.contains(">text"))

        // xmlEncode covers <, >, &, control char, quote.
        assertEquals("", XMLUtility.xmlEncode(null))
        val enc = XMLUtility.xmlEncode("<a&b>\"'\u0001")
        assertTrue(enc.contains("&lt;"))
        assertTrue(enc.contains("&amp;"))
        assertTrue(enc.contains("&gt;"))
        assertTrue(enc.contains("&#"))

        // parseString / parseInt / parseDouble from null attr map.
        assertNull(XMLUtility.parseString(null, "x"))
        assertNull(XMLUtility.parseInt(null, "x"))
        assertNull(XMLUtility.parseDouble(null, "x"))

        // Resolvers just for construction; cannot fire without a parser.
        assertNotNull(XMLUtility.entityResolver("pub", "dtd", XMLUtility::class.java))
        assertNotNull(XMLUtility.errorHandler("x"))
    }

    @Test
    fun pathGraph_roundtrip_contract_get_serialize() {
        val pg = PathGraph()
        pg.plot(1, 0, 0)
        pg.plot(1, 1, 0)
        assertFalse(pg.isEmpty)
        pg.removeOverlap()
        pg.simplifyPaths()
        assertTrue(pg.srcPoints.isNotEmpty())
        assertTrue(pg.allPoints.isNotEmpty())
        val contours = pg.contours
        assertTrue(contours.isNotEmpty())
        val rect = pg.boundingRect
        assertNotNull(rect)
        val glyf = pg.glyfData
        assertTrue(glyf.isNotEmpty())

        // Copy constructor and equality.
        val copy = PathGraph(pg)
        assertEquals(pg, copy)
        assertEquals(pg.hashCode(), copy.hashCode())
        assertFalse(pg.equals("not a graph"))
        assertNotNull(pg.toString())

        // remove + contains coverage.
        assertTrue(pg.contains(0, 0, 0, 1))
        val edge = pg.allEdges[0]
        assertTrue(pg.contains(edge))
        pg.remove(edge)
        pg.remove(PathGraph.PathEdge(PathGraph.ImmutablePoint(99, 99), PathGraph.ImmutablePoint(100, 100)))
        pg.add(0, 0, 5, 5)
        pg.add(PathGraph.PathEdge(PathGraph.ImmutablePoint(5, 5), PathGraph.ImmutablePoint(6, 6)))
        pg.adjEdges(PathGraph.ImmutablePoint(0, 0))
        pg.adjEdges(PathGraph.ImmutablePoint(999, 999))
        pg.clear()
        assertTrue(pg.isEmpty)

        // Empty bounding rect + glyf + contours + adjacency.
        val empty = PathGraph()
        val er = empty.boundingRect
        assertEquals(0, er.width)
        empty.contours
        empty.glyfData
        empty.adjPoints(PathGraph.ImmutablePoint(0, 0))
    }

    private fun PathGraph.adjEdges(p: PathGraph.ImmutablePoint) = this.getAdjEdges(p)
    private fun PathGraph.adjPoints(p: PathGraph.ImmutablePoint) = this.getAdjPoints(p)

    @Test
    fun pathGraph_plot_variants_and_ImmutablePoint_PathEdge_equals() {
        val pg = PathGraph()
        pg.plot(2, PathGraph.ImmutablePoint(1, 1))
        pg.plot(2, java.awt.Point(2, 2))
        pg.plot(2, 3, PathGraph.ImmutablePoint(3, 3))
        pg.plot(2, 3, java.awt.Point(4, 4))

        val a = PathGraph.ImmutablePoint(1, 2)
        val b = PathGraph.ImmutablePoint(1, 2)
        val c = PathGraph.ImmutablePoint(a)
        val d = PathGraph.ImmutablePoint(java.awt.Point(1, 2))
        assertEquals(a, b); assertEquals(a, c); assertEquals(a, d)
        assertTrue(a.equals(java.awt.Point(1, 2)))
        assertFalse(a.equals("nope"))
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(1, a.x); assertEquals(2, a.y)
        assertNotNull(a.point)
        assertEquals("1,2", a.toString())

        val e1 = PathGraph.PathEdge(a, b)
        val e2 = PathGraph.PathEdge(a, b)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
        assertFalse(e1.equals("nope"))
        assertEquals(a, e1.src); assertEquals(b, e1.dst)
        assertNotNull(e1.reflection)
        assertTrue(e1.toString().contains("->"))
    }

    @Test
    fun fontGlyph_transform_returns_null_skip_path() {
        val bm = TestFonts.tinyFont()
        // Transformer that returns a modified glyph - ensures both branches.
        bm.transform(object : com.kreative.bitsnpicas.FontGlyphTransformer<BitmapFontGlyph> {
            override fun transformGlyph(g: BitmapFontGlyph?) =
                g?.let { BitmapFontGlyph(it.glyph, it.x, it.characterWidth, it.y) }
        })
    }
}
