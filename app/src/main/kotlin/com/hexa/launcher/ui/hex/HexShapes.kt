package com.hexa.launcher.ui.hex

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.hexa.core.layout.HexMath
import com.hexa.core.layout.SQRT3
import kotlin.math.min
import kotlin.math.sqrt

object HexPaths {

    /**
     * Writes a convex polygon with rounded corners into [path]. [pts] holds x0,y0,x1,y1...;
     * [radius] is how far along each edge the rounding starts (capped at half an edge).
     */
    fun roundedPolygon(path: Path, pts: FloatArray, radius: Float) {
        path.reset()
        val n = pts.size / 2
        for (i in 0 until n) {
            val vx = pts[i * 2]
            val vy = pts[i * 2 + 1]
            val p = (i - 1 + n) % n
            val q = (i + 1) % n
            val pxv = pts[p * 2] - vx
            val pyv = pts[p * 2 + 1] - vy
            val qxv = pts[q * 2] - vx
            val qyv = pts[q * 2 + 1] - vy
            val d1 = sqrt(pxv * pxv + pyv * pyv)
            val d2 = sqrt(qxv * qxv + qyv * qyv)
            val r = if (d1 < 0.01f || d2 < 0.01f) 0f else min(radius, min(d1, d2) / 2f)
            val ax = if (d1 < 0.01f) vx else vx + pxv * (r / d1)
            val ay = if (d1 < 0.01f) vy else vy + pyv * (r / d1)
            val bx = if (d2 < 0.01f) vx else vx + qxv * (r / d2)
            val by = if (d2 < 0.01f) vy else vy + qyv * (r / d2)
            if (i == 0) path.moveTo(ax, ay) else path.lineTo(ax, ay)
            if (r > 0f) {
                // Quadratic corner (control point = vertex) expressed as a cubic.
                path.cubicTo(
                    ax + 2f / 3f * (vx - ax), ay + 2f / 3f * (vy - ay),
                    bx + 2f / 3f * (vx - bx), by + 2f / 3f * (vy - by),
                    bx, by,
                )
            }
        }
        path.close()
    }

    fun hexagon(path: Path, cx: Float, cy: Float, radius: Float, pointyTop: Boolean, cornerFrac: Float) {
        roundedPolygon(path, HexMath.vertices(cx, cy, radius, pointyTop), radius * cornerFrac)
    }

    /**
     * Shape between a pointy-top hexagon (t = 0) filling the box and a rounded rectangle (t = 1).
     * Each hexagon vertex slides to a rectangle point, so the outline reads as one object changing.
     */
    fun morph(path: Path, w: Float, h: Float, t: Float, hexCornerFrac: Float, rectCorner: Float) {
        val hex = floatArrayOf(w / 2f, 0f, w, h / 4f, w, h * 3f / 4f, w / 2f, h, 0f, h * 3f / 4f, 0f, h / 4f)
        val rect = floatArrayOf(w / 2f, 0f, w, 0f, w, h, w / 2f, h, 0f, h, 0f, 0f)
        val pts = FloatArray(12) { i -> hex[i] + (rect[i] - hex[i]) * t }
        val hexRadius = min(w, h) / 2f * hexCornerFrac
        roundedPolygon(path, pts, hexRadius + (rectCorner - hexRadius) * t)
    }
}

/** Regular hexagon fitted into the component bounds. */
class HexagonShape(private val pointyTop: Boolean = true, private val cornerFrac: Float = 0.14f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = if (pointyTop) min(size.height / 2f, size.width / SQRT3) else min(size.width / 2f, size.height / SQRT3)
        val path = Path()
        HexPaths.hexagon(path, size.width / 2f, size.height / 2f, r, pointyTop, cornerFrac)
        return Outline.Generic(path)
    }

    override fun equals(other: Any?) = other is HexagonShape && other.pointyTop == pointyTop && other.cornerFrac == cornerFrac
    override fun hashCode() = pointyTop.hashCode() * 31 + cornerFrac.hashCode()
}

/** Hexagon → rounded rectangle morph used by the music hive. */
class MorphShape(private val progress: Float, private val hexCornerFrac: Float, private val rectCornerPx: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        HexPaths.morph(path, size.width, size.height, progress, hexCornerFrac, rectCornerPx)
        return Outline.Generic(path)
    }
}

/** Horizontally elongated hexagon (pointed ends), used for the search bar. */
object LongHexShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val h = size.height
        val w = size.width
        val k = min(h / (2f * SQRT3) * 1.6f, w / 4f)
        val pts = floatArrayOf(0f, h / 2f, k, 0f, w - k, 0f, w, h / 2f, w - k, h, k, h)
        val path = Path()
        HexPaths.roundedPolygon(path, pts, h * 0.22f)
        return Outline.Generic(path)
    }
}
