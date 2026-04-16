package com.kreative.bitsnpicas.core

/**
 * Pure-Kotlin multiplatform bitmap font representation.
 *
 * Mirrors the essential data of the frozen Java
 * `com.kreative.bitsnpicas.BitmapFont` without any `java.*` dependency.
 * Glyphs are keyed by Unicode codepoint.
 */
public data class BitmapFont(
    /** Glyphs indexed by Unicode codepoint. */
    val glyphs: Map<Int, BitmapGlyph>,
    /** Font-level em-ascent (pixels above baseline). */
    val emAscent: Int,
    /** Font-level em-descent (pixels below baseline, typically 0 for PSF). */
    val emDescent: Int,
    /** Line ascent. */
    val lineAscent: Int,
    /** Line descent. */
    val lineDescent: Int,
    /** x-height. */
    val xHeight: Int,
    /** Cap height. */
    val capHeight: Int,
    /** Line gap. */
    val lineGap: Int,
    /** Default width for new glyphs. */
    val newGlyphWidth: Int,
    /** Optional font-family name. */
    val name: String? = null,
)
