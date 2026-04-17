package com.kreative.bitsnpicas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kreative.bitsnpicas.core.BitmapFont
import com.kreative.bitsnpicas.core.BitmapGlyph

/**
 * Compose Desktop glyph renderer — renders BitmapGlyph via Compose
 * Canvas API. This is the "green" renderer that must match the
 * Swing "blue" oracle pixel-for-pixel.
 */
object ComposeGlyphRenderer {

    @Composable
    fun GlyphCanvas(glyph: BitmapGlyph, scale: Int = 4, modifier: Modifier = Modifier) {
        val w = glyph.width * scale
        val h = glyph.height * scale
        Canvas(modifier = modifier.size(w.dp, h.dp)) {
            for (row in glyph.bitmap.indices) {
                for (col in glyph.bitmap[row].indices) {
                    val v = glyph.bitmap[row][col] and 0xFF
                    if (v > 0) {
                        drawRect(
                            color = Color(0f, 0f, 0f, v / 255f),
                            topLeft = Offset((col * scale).toFloat(), (row * scale).toFloat()),
                            size = Size(scale.toFloat(), scale.toFloat()),
                        )
                    }
                }
            }
        }
    }
}
