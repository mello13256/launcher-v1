package com.hexa.core.layout

import com.hexa.core.model.CENTER_SLOT
import com.hexa.core.model.LayoutConfig
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

const val SQRT3 = 1.7320508f

/** Position and size of one slot in pixels. [radius] is the circumradius of the drawn hexagon. */
data class SlotGeometry(
    val slot: Int,
    val cx: Float,
    val cy: Float,
    val radius: Float,
    /** Radius of the touch cell: the drawn hexagon plus its share of the gap, so taps never fall into dead zones. */
    val cellRadius: Float,
)

/**
 * Resolved geometry of a composition. Pure function of preset, config and available size, so it
 * is only recomputed when one of those changes.
 */
data class LayoutGeometry(
    val slots: List<SlotGeometry>,
    /** True for pointy-top hexagons (portrait). Landscape transposes the grid into flat-top hexagons. */
    val pointyTop: Boolean,
    /** Drawn radius of a regular hive in px. */
    val radius: Float,
    val width: Float,
    val height: Float,
) {
    fun slot(index: Int): SlotGeometry = slots[index]

    /**
     * Slot under ([x], [y]), or null. Uses touch cells and resolves overlaps by distance, so the
     * whole composition is covered without gaps.
     */
    fun slotAt(x: Float, y: Float): Int? {
        var best: SlotGeometry? = null
        var bestDist = Float.MAX_VALUE
        for (s in slots) {
            if (!HexMath.contains(x - s.cx, y - s.cy, s.cellRadius, pointyTop)) continue
            val d = hypot(x - s.cx, y - s.cy)
            if (d < bestDist) {
                bestDist = d
                best = s
            }
        }
        return best?.slot
    }
}

object HexMath {
    /** Point-in-hexagon test relative to the hexagon centre. */
    fun contains(dx: Float, dy: Float, radius: Float, pointyTop: Boolean): Boolean {
        val ax = abs(if (pointyTop) dx else dy)
        val ay = abs(if (pointyTop) dy else dx)
        if (ax > radius * SQRT3 / 2f) return false
        return ay <= radius - ax / SQRT3
    }

    /** Vertices of a hexagon as x0,y0,x1,y1... starting at the top (pointy) or right (flat) vertex. */
    fun vertices(cx: Float, cy: Float, radius: Float, pointyTop: Boolean): FloatArray {
        val out = FloatArray(12)
        for (i in 0 until 6) {
            val angle = Math.toRadians((if (pointyTop) -90.0 else 0.0) + 60.0 * i)
            out[i * 2] = cx + radius * kotlin.math.cos(angle).toFloat()
            out[i * 2 + 1] = cy + radius * kotlin.math.sin(angle).toFloat()
        }
        return out
    }
}

object HexLayoutEngine {

    /**
     * Fits [spec] into a [width]x[height] px area. All slots (including free ones) are fitted so the
     * composition does not jump around while hives are being moved.
     */
    fun compute(
        spec: PresetSpec,
        config: LayoutConfig,
        width: Float,
        height: Float,
        density: Float,
        landscape: Boolean = width > height,
    ): LayoutGeometry {
        val gap = config.gap.coerceIn(0f, 0.3f)
        val centerScale = config.centerScale.coerceIn(1f, 1.4f)
        val drawn = 1f / (1f + gap)
        val centerDrawn = drawn * centerScale
        // The centre grows by (scale - 1) in radius; push every other slot outward by the growth of
        // its apothem so the neighbours keep the same gap. Radial pushes only increase distances
        // between the other slots, so nothing overlaps.
        val push = (centerScale - 1f) * drawn * (SQRT3 / 2f) * 1.05f

        val pointyTop = !landscape
        val unitX = FloatArray(spec.slotCount)
        val unitY = FloatArray(spec.slotCount)
        spec.slots.forEachIndexed { i, pos ->
            var x = pos.col * SQRT3
            var y = pos.row * 1.5f
            if (i != CENTER_SLOT) {
                val len = sqrt(x * x + y * y)
                if (len > 0f) {
                    x += x / len * push
                    y += y / len * push
                }
            }
            if (landscape) {
                val t = x; x = y; y = t
            }
            unitX[i] = x
            unitY[i] = y
        }

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in 0 until spec.slotCount) {
            val r = if (i == CENTER_SLOT) centerDrawn else drawn
            val hw = if (pointyTop) r * SQRT3 / 2f else r
            val hh = if (pointyTop) r else r * SQRT3 / 2f
            minX = min(minX, unitX[i] - hw)
            maxX = maxOf(maxX, unitX[i] + hw)
            minY = min(minY, unitY[i] - hh)
            maxY = maxOf(maxY, unitY[i] + hh)
        }
        val bw = maxX - minX
        val bh = maxY - minY

        val maxUnit = (config.maxRadiusDp * density) / drawn
        val fit = min(width / bw, height / bh)
        val k = (min(fit, maxUnit) * config.scale.coerceIn(0.5f, 1f)).coerceAtLeast(0f)

        val offsetX = (width - bw * k) / 2f - minX * k
        val bias = config.verticalBias.coerceIn(-1f, 1f)
        val offsetY = (height - bh * k) * (bias + 1f) / 2f - minY * k

        val slots = (0 until spec.slotCount).map { i ->
            val r = if (i == CENTER_SLOT) centerDrawn else drawn
            SlotGeometry(
                slot = i,
                cx = offsetX + unitX[i] * k,
                cy = offsetY + unitY[i] * k,
                radius = r * k,
                // The radial push opens small gaps between ring cells; enlarge touch cells to cover
                // them. Overlaps are resolved by distance in slotAt().
                cellRadius = (r * (1f + gap) + push) * k * 1.08f,
            )
        }
        return LayoutGeometry(slots, pointyTop, drawn * k, width, height)
    }
}
