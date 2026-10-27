package com.hexa.core.layout

import com.hexa.core.model.CENTER_SLOT
import com.hexa.core.model.LayoutConfig
import com.hexa.core.model.LayoutPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HexLayoutTest {

    private val screens = listOf(
        Triple(1080f, 1900f, 2.75f), // tall phone portrait (area under status bar, above search)
        Triple(720f, 1100f, 2f),     // small phone
        Triple(1900f, 900f, 2.75f),  // phone landscape
        Triple(1600f, 2300f, 2f),    // tablet portrait
        Triple(1440f, 1440f, 3f),    // square / foldable
    )

    private fun geometries(config: LayoutConfig = LayoutConfig()) = LayoutPreset.entries.flatMap { preset ->
        screens.map { (w, h, d) -> Triple(preset, HexLayoutEngine.compute(LayoutPresets.of(preset), config, w, h, d), Pair(w, h)) }
    }

    @Test
    fun everyPresetHasRoomForAllHives() {
        for (p in LayoutPreset.entries) {
            val spec = LayoutPresets.of(p)
            assertTrue(spec.slotCount - 1 >= 15)
            assertEquals(15, spec.fillOrder.size)
        }
    }

    @Test
    fun hexagonsFitInsideAvailableArea() {
        for ((preset, g, size) in geometries()) {
            for (s in g.slots) {
                val v = HexMath.vertices(s.cx, s.cy, s.radius, g.pointyTop)
                for (i in 0 until 6) {
                    assertTrue("$preset $size x", v[i * 2] >= -0.5f && v[i * 2] <= size.first + 0.5f)
                    assertTrue("$preset $size y", v[i * 2 + 1] >= -0.5f && v[i * 2 + 1] <= size.second + 0.5f)
                }
            }
        }
    }

    @Test
    fun hexagonsNeverOverlap() {
        for (cs in listOf(1f, 1.2f, 1.4f)) for (gap in listOf(0f, 0.07f, 0.3f)) {
            for ((preset, g, _) in geometries(LayoutConfig(centerScale = cs, gap = gap))) {
                val polys = g.slots.map { HexMath.vertices(it.cx, it.cy, it.radius, g.pointyTop) }
                for (i in polys.indices) for (j in i + 1 until polys.size) {
                    assertFalse("$preset cs=$cs gap=$gap: slots $i and $j overlap", overlap(polys[i], polys[j]))
                }
            }
        }
    }

    @Test
    fun centreIsLargerAndInTheMiddle() {
        for ((_, g, size) in geometries(LayoutConfig(verticalBias = 0f))) {
            val c = g.slot(CENTER_SLOT)
            assertTrue(c.radius > g.radius)
            assertEquals(size.first / 2f, c.cx, 1f)
        }
    }

    @Test
    fun landscapeUsesFlatTopHexagons() {
        val g = HexLayoutEngine.compute(LayoutPresets.HONEYCOMB, LayoutConfig(), 2000f, 900f, 2.75f)
        assertFalse(g.pointyTop)
        assertTrue(HexLayoutEngine.compute(LayoutPresets.HONEYCOMB, LayoutConfig(), 900f, 2000f, 2.75f).pointyTop)
    }

    @Test
    fun maxRadiusLimitsTabletSize() {
        val g = HexLayoutEngine.compute(LayoutPresets.HONEYCOMB, LayoutConfig(maxRadiusDp = 60f), 3000f, 4000f, 2f)
        assertEquals(120f, g.radius, 0.5f)
    }

    @Test
    fun usesMostOfTheLimitingDimension() {
        val g = HexLayoutEngine.compute(LayoutPresets.HONEYCOMB, LayoutConfig(maxRadiusDp = 999f), 1080f, 2000f, 2.75f)
        val minX = g.slots.minOf { it.cx - it.radius * SQRT3 / 2f }
        val maxX = g.slots.maxOf { it.cx + it.radius * SQRT3 / 2f }
        assertTrue("composition should span the width, spans ${maxX - minX}", maxX - minX > 1080f * 0.95f)
    }

    @Test
    fun hitTestFindsEachSlotAndCoversGaps() {
        for ((preset, g, _) in geometries()) {
            for (s in g.slots) assertEquals("$preset", s.slot, g.slotAt(s.cx, s.cy))
            // Midpoint between two neighbours (inside the gap) still hits one of them.
            val a = g.slot(LayoutPresets.of(preset).fillOrder[0])
            val b = g.slot(LayoutPresets.of(preset).fillOrder[1])
            val hit = g.slotAt((a.cx + b.cx) / 2f, (a.cy + b.cy) / 2f)
            assertTrue("$preset gap hit $hit", hit == a.slot || hit == b.slot)
            assertNull(g.slotAt(-500f, -500f))
        }
    }

    @Test
    fun hitTestRespectsHexagonShape() {
        assertTrue(HexMath.contains(0f, -0.99f, 1f, pointyTop = true))
        assertFalse(HexMath.contains(0.8f, -0.8f, 1f, pointyTop = true)) // outside the top-right edge
        assertTrue(HexMath.contains(0.86f, 0f, 1f, pointyTop = true))
        assertFalse(HexMath.contains(0f, 0.95f, 1f, pointyTop = false))
    }

    @Test
    fun scaleShrinksComposition() {
        val full = HexLayoutEngine.compute(LayoutPresets.TOWER, LayoutConfig(scale = 1f), 1080f, 2000f, 2.75f)
        val small = HexLayoutEngine.compute(LayoutPresets.TOWER, LayoutConfig(scale = 0.7f), 1080f, 2000f, 2.75f)
        assertEquals(full.radius * 0.7f, small.radius, 0.5f)
    }

    // --- separating axis test for convex polygons -------------------------------------------

    private fun overlap(a: FloatArray, b: FloatArray): Boolean {
        for (poly in listOf(a, b)) {
            for (i in 0 until 6) {
                val x1 = poly[i * 2]; val y1 = poly[i * 2 + 1]
                val x2 = poly[(i + 1) % 6 * 2]; val y2 = poly[(i + 1) % 6 * 2 + 1]
                val nx = -(y2 - y1); val ny = x2 - x1
                val (minA, maxA) = project(a, nx, ny)
                val (minB, maxB) = project(b, nx, ny)
                // Allow touching (floating point) but not penetration.
                val len = kotlin.math.sqrt(nx * nx + ny * ny)
                if (maxA <= minB + 0.01f * len || maxB <= minA + 0.01f * len) return false
            }
        }
        return true
    }

    private fun project(p: FloatArray, nx: Float, ny: Float): Pair<Float, Float> {
        var min = Float.MAX_VALUE; var max = -Float.MAX_VALUE
        for (i in 0 until 6) {
            val d = p[i * 2] * nx + p[i * 2 + 1] * ny
            if (d < min) min = d
            if (d > max) max = d
        }
        return min to max
    }
}
