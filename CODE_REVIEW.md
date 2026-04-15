# Code Review: Bits&Picas Font Tool

## Overview

Bits&Picas is a Java-based bitmap font manipulation tool supporting multiple
font formats (BDF, PSF, FNT, HEX, Playdate, etc.). The codebase handles font
parsing, glyph management, and export/import across diverse formats. The core
components include `BitmapFont` (font container), `BitmapFontGlyph` (individual
glyph data), and numerous importers/exporters for different formats.

This review focuses on the most critical font-handling files — importers,
exporters, and the core glyph model — and lists concrete, actionable findings.

---

## Critical

### 1. Array-index-out-of-bounds in PSF glyph reader

`main/java/BitsNPicas/src/com/kreative/bitsnpicas/importer/PSFBitmapFontImporter.java:107-115`

The nested loop increments byte index `j` independently of the row counter `y`,
then dereferences `data[j]` without a bounds check. Malformed PSF input can
trigger `ArrayIndexOutOfBoundsException`.

```java
for (int j = 0, y = 0; y < height; y++) {
    for (int x = 0; x < width; j++) {
        for (int m = 0x80; x < width && m != 0; x++, m >>= 1) {
            if ((data[j] & m) != 0) {
```

**Fix:** Validate `j < data.length` before indexing, or compute the byte offset
strictly from `y * bytesPerRow + xByte`.

### 2. Unchecked array access in FNT glyph reader

`main/java/BitsNPicas/src/com/kreative/bitsnpicas/importer/FNTBitmapFontImporter.java:149-159`

`dx` is computed from untrusted header values and used to index `data[dx]`
without verifying `dx < data.length`. Crafted `.FNT` files can crash the
importer or read past the buffer.

**Fix:** Validate `0 <= dx < data.length` before each access.

### 3. Unbounded allocations in PSF importer (OOM)

`main/java/BitsNPicas/src/com/kreative/bitsnpicas/importer/PSFBitmapFontImporter.java:93,95,100,102`

`headerSize` and `numGlyphs` are read directly from the file and used to
allocate arrays (`new byte[headerSize - 32]`, `new byte[numGlyphs][][]`) with
no sanity limits. A malicious PSF can trigger `OutOfMemoryError`.

**Fix:** Reject values above reasonable limits
(e.g. `if (numGlyphs > 0x200000) throw new IOException(...)`).

### 4. Resource leak — BDF importer never closes Scanner / FileInputStream

`main/java/BitsNPicas/src/com/kreative/bitsnpicas/importer/BDFBitmapFontImporter.java:35-46`

```java
public BitmapFont[] importFont(File file) throws IOException {
    return importFont(new Scanner(new FileInputStream(file), "UTF-8"));
}
```

Neither the `Scanner` nor the underlying `FileInputStream` is closed. Repeated
imports exhaust file descriptors. The same pattern appears in
`HexBitmapFontImporter.java:33` and `PlaydateBitmapFontImporter.java:103`.

**Fix:** Use try-with-resources around the Scanner/stream.

### 5. Zero-dimension glyph composition can throw

`main/java/BitsNPicas/src/com/kreative/bitsnpicas/BitmapFontGlyph.java:345-359`

```java
if (y1 < y0 || x1 < x0) return null;
byte[][] g = new byte[y1 - y0][x1 - x0];
```

The guard uses `<` but permits `y1 == y0` or `x1 == x0`, allocating a
zero-dimensional array that later writes still address. Also, if `glyph.glyph`
or any row inside is `null`, the outer loop NPEs.

**Fix:** Use strict inequality (`y1 <= y0 || x1 <= x0`) and null-check rows
before dereferencing.

---

## Major

### 6. BDF bitmap reader silently accepts truncated glyphs

`main/java/BitsNPicas/src/com/kreative/bitsnpicas/importer/BDFBitmapFontImporter.java:162-171`

If `ENDCHAR` appears before `glyph.length` rows have been read, `readBitmap`
returns `true` and leaves the remaining rows zero-filled without signalling an
error. Corrupt BDF files produce silently-wrong glyphs.

**Fix:** Track the populated row count and return `false` (or log a warning)
when the bitmap is incomplete.

### 7. BDF export iterates over the entire Unicode codepoint range

`main/java/BitsNPicas/src/com/kreative/bitsnpicas/exporter/BDFBitmapFontExporter.java:49,90`

```java
for (int i = 0; i < 0x110000; i++) {
    if (font.containsCharacter(i)) { ... }
}
```

Every export pays ~1.1M iterations regardless of font size.

**Fix:** Iterate over `font.characters(false).keySet()` (or the sorted variant)
and skip the scan.

### 8. Charset lookup failures are hidden on stderr

`main/java/BitsNPicas/src/com/kreative/bitsnpicas/importer/BDFBitmapFontImporter.java:105-107`

Unsupported CHARSET_REGISTRY values print to stderr but parsing continues with
the wrong encoding, producing silently incorrect codepoints.

**Fix:** Surface the error to the caller (throw `IOException` or attach a
warning to the returned font).

### 9. Encoding decode produces arbitrary codepoints

`main/java/BitsNPicas/src/com/kreative/bitsnpicas/importer/BDFBitmapFontImporter.java:127-134`

`new String(toByteArray(encoding), cs)` can decode to `U+FFFD` (replacement
char) or unassigned/private-use codepoints and is accepted as the glyph's
encoding with no validation.

**Fix:** Reject codepoints that decode to the Unicode replacement char or to
surrogate halves.

### 10. `StringBuffer` used in single-threaded import path

`main/java/BitsNPicas/src/com/kreative/bitsnpicas/importer/FNTBitmapFontImporter.java:172`

`StringBuffer` is synchronized and unnecessary here. Replace with
`StringBuilder`.

---

## Minor

### 11. `HexBitmapFontImporter` can divide by zero

`main/java/BitsNPicas/src/com/kreative/bitsnpicas/importer/HexBitmapFontImporter.java:50-54`

Derived `height` can be `0` for degenerate input, after which
`int width = hex.length / height;` throws `ArithmeticException`.

**Fix:** Guard with `if (height <= 0) continue;`.

### 12. Unsigned byte semantics in FNT importer are confusing

`main/java/BitsNPicas/src/com/kreative/bitsnpicas/importer/FNTBitmapFontImporter.java:82-83`

`firstChar`/`lastChar` are read with `readUnsignedByte()` and immediately used
in signed arithmetic. No bug today, but easy to misread.

**Fix:** Rename or add a comment stating the values are in `[0, 255]`.

### 13. `BitmapFontGlyph` empty-glyph invariants are inconsistent

`main/java/BitsNPicas/src/com/kreative/bitsnpicas/BitmapFontGlyph.java:17-21`

Constructor sets `y = glyph.length`, but nothing enforces a non-empty bitmap.
Empty glyphs yield `y = 0`, producing inconsistent baseline math downstream.

**Fix:** Either enforce a non-empty bitmap invariant in the constructor or
special-case empty glyphs everywhere they are consumed.

### 14. Row-width assumption in PSF importer

`main/java/BitsNPicas/src/com/kreative/bitsnpicas/importer/PSFBitmapFontImporter.java:106`

Code assumes all rows are the same width and that
`data.length == height * ceil(width/8)`. Short inputs access uninitialised
memory (implicit zero-fill), producing subtly wrong glyphs.

**Fix:** Assert `data.length == height * bytesPerRow` and reject otherwise.

---

## Nit

### 15. `getPixel()` coordinate system is undocumented

`main/java/BitsNPicas/src/com/kreative/bitsnpicas/BitmapFontGlyph.java:152-162`

The `int iy = y + getY()` transform is only obvious if you already know the
coordinate convention. Add Javadoc describing the Y-axis direction and the
meaning of `getY()`.

---

## Summary

| Severity | Count |
| --- | --- |
| Critical | 5 |
| Major    | 5 |
| Minor    | 4 |
| Nit      | 1 |

**Top priorities:**

1. Bounds-check every array access driven by header-derived sizes in the PSF
   and FNT importers.
2. Cap the maximum allocation sizes in the PSF importer.
3. Close the BDF/Hex/Playdate `Scanner`s with try-with-resources.
4. Fix the empty-dimension guard and row null-check in
   `BitmapFontGlyph.compose`.

These account for every way a malformed input file can crash or stall the
application and should be treated as release blockers.
