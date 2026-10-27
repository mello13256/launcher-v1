package com.hexa.launcher.ui.hex

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Small vector glyphs drawn directly (music note, transport controls...). Avoids pulling the large
 * extended icon set for half a dozen symbols.
 */
enum class Glyph { NOTE, PLAY, PAUSE, NEXT, PREVIOUS, PLUS, ALERT, CLOSE, SEARCH, GEAR, EDIT }

fun DrawScope.drawGlyph(glyph: Glyph, center: Offset, size: Float, color: Color) {
    val s = size
    val x = center.x - s / 2f
    val y = center.y - s / 2f
    when (glyph) {
        Glyph.NOTE -> {
            val stroke = s * 0.09f
            // Two beamed eighth notes.
            drawOval(color, Offset(x + s * 0.08f, y + s * 0.66f), Size(s * 0.32f, s * 0.24f))
            drawOval(color, Offset(x + s * 0.58f, y + s * 0.56f), Size(s * 0.32f, s * 0.24f))
            drawRect(color, Offset(x + s * 0.34f, y + s * 0.18f), Size(stroke, s * 0.6f))
            drawRect(color, Offset(x + s * 0.84f, y + s * 0.08f), Size(stroke, s * 0.6f))
            val beam = Path().apply {
                moveTo(x + s * 0.34f, y + s * 0.18f)
                lineTo(x + s * 0.84f + stroke, y + s * 0.08f)
                lineTo(x + s * 0.84f + stroke, y + s * 0.24f)
                lineTo(x + s * 0.34f, y + s * 0.34f)
                close()
            }
            drawPath(beam, color)
        }
        Glyph.PLAY -> drawPath(Path().apply {
            moveTo(x + s * 0.24f, y + s * 0.14f)
            lineTo(x + s * 0.86f, y + s * 0.5f)
            lineTo(x + s * 0.24f, y + s * 0.86f)
            close()
        }, color)
        Glyph.PAUSE -> {
            drawRoundRect(color, Offset(x + s * 0.2f, y + s * 0.14f), Size(s * 0.2f, s * 0.72f), CornerRadius(s * 0.04f))
            drawRoundRect(color, Offset(x + s * 0.6f, y + s * 0.14f), Size(s * 0.2f, s * 0.72f), CornerRadius(s * 0.04f))
        }
        Glyph.NEXT -> {
            drawPath(Path().apply {
                moveTo(x + s * 0.16f, y + s * 0.18f)
                lineTo(x + s * 0.68f, y + s * 0.5f)
                lineTo(x + s * 0.16f, y + s * 0.82f)
                close()
            }, color)
            drawRect(color, Offset(x + s * 0.7f, y + s * 0.18f), Size(s * 0.13f, s * 0.64f))
        }
        Glyph.PREVIOUS -> {
            drawPath(Path().apply {
                moveTo(x + s * 0.84f, y + s * 0.18f)
                lineTo(x + s * 0.32f, y + s * 0.5f)
                lineTo(x + s * 0.84f, y + s * 0.82f)
                close()
            }, color)
            drawRect(color, Offset(x + s * 0.17f, y + s * 0.18f), Size(s * 0.13f, s * 0.64f))
        }
        Glyph.PLUS -> {
            val t = s * 0.12f
            drawRoundRect(color, Offset(x + s * 0.5f - t / 2f, y + s * 0.18f), Size(t, s * 0.64f), CornerRadius(t / 2f))
            drawRoundRect(color, Offset(x + s * 0.18f, y + s * 0.5f - t / 2f), Size(s * 0.64f, t), CornerRadius(t / 2f))
        }
        Glyph.ALERT -> {
            drawCircle(color, s / 2f, center)
            val ink = Color(0xFF1A1A1A)
            drawRoundRect(ink, Offset(center.x - s * 0.06f, y + s * 0.2f), Size(s * 0.12f, s * 0.38f), CornerRadius(s * 0.06f))
            drawCircle(ink, s * 0.07f, Offset(center.x, y + s * 0.74f))
        }
        Glyph.CLOSE -> {
            val st = Stroke(width = s * 0.12f)
            drawLine(color, Offset(x + s * 0.22f, y + s * 0.22f), Offset(x + s * 0.78f, y + s * 0.78f), st.width)
            drawLine(color, Offset(x + s * 0.78f, y + s * 0.22f), Offset(x + s * 0.22f, y + s * 0.78f), st.width)
        }
        Glyph.SEARCH -> {
            drawCircle(color, s * 0.27f, Offset(x + s * 0.42f, y + s * 0.42f), style = Stroke(s * 0.11f))
            drawLine(color, Offset(x + s * 0.62f, y + s * 0.62f), Offset(x + s * 0.86f, y + s * 0.86f), s * 0.12f)
        }
        Glyph.GEAR -> {
            drawCircle(color, s * 0.3f, center, style = Stroke(s * 0.12f))
            for (i in 0 until 6) {
                val a = Math.toRadians(60.0 * i)
                val dx = kotlin.math.cos(a).toFloat()
                val dy = kotlin.math.sin(a).toFloat()
                drawLine(color, Offset(center.x + dx * s * 0.34f, center.y + dy * s * 0.34f), Offset(center.x + dx * s * 0.47f, center.y + dy * s * 0.47f), s * 0.13f)
            }
        }
        Glyph.EDIT -> {
            drawLine(color, Offset(x + s * 0.2f, y + s * 0.8f), Offset(x + s * 0.78f, y + s * 0.22f), s * 0.14f)
            drawLine(color, Offset(x + s * 0.14f, y + s * 0.88f), Offset(x + s * 0.3f, y + s * 0.84f), s * 0.1f)
        }
    }
}

@Composable
fun GlyphIcon(glyph: Glyph, color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) { drawGlyph(glyph, center, this.size.minDimension, color) }
}
