package com.hexa.core.layout

import com.hexa.core.model.LayoutPreset
import com.hexa.core.model.REGULAR_HIVE_COUNT

/**
 * A slot position on a pointy-top hexagonal grid. [col] is measured in hexagon widths (half
 * values on odd rows), [row] in rows. Slot 0 is always the centre (0, 0).
 */
data class GridPos(val col: Float, val row: Int)

/**
 * A preset is a fixed set of slots. There are more slots than hives, so the user can choose where
 * the free cells are when moving hives around. [fillOrder] gives the default slot of hive 0..14.
 */
class PresetSpec(
    val preset: LayoutPreset,
    val slots: List<GridPos>,
    val fillOrder: List<Int>,
) {
    val slotCount: Int get() = slots.size

    init {
        require(slots.first() == GridPos(0f, 0)) { "slot 0 must be the centre" }
        require(fillOrder.size == REGULAR_HIVE_COUNT) { "fill order must cover the 15 regular hives" }
        require(fillOrder.toSet().size == fillOrder.size && 0 !in fillOrder)
        require(fillOrder.all { it in 1 until slots.size })
        require(slots.toSet().size == slots.size) { "duplicated slot" }
    }
}

object LayoutPresets {

    private fun row(row: Int, vararg cols: Float) = cols.map { GridPos(it, row) }

    /** 4 hexagons wide, 5 rows: a compact honeycomb for portrait phones. 17 slots. */
    val HONEYCOMB = PresetSpec(
        preset = LayoutPreset.HONEYCOMB,
        slots = listOf(GridPos(0f, 0)) +
            row(-2, -1f, 0f, 1f) +                 // 1..3
            row(-1, -1.5f, -0.5f, 0.5f, 1.5f) +    // 4..7
            row(0, -1f, 1f) +                      // 8..9
            row(1, -1.5f, -0.5f, 0.5f, 1.5f) +     // 10..13
            row(2, -1f, 0f, 1f),                   // 14..16
        // Inner ring clockwise, then outer ring; slot 15 (bottom centre) starts free.
        fillOrder = listOf(5, 6, 9, 12, 11, 8, 1, 2, 3, 7, 13, 16, 14, 10, 4),
    )

    /** 3 hexagons wide, 7 rows: taller, larger hexagons on narrow screens. 17 slots. */
    val TOWER = PresetSpec(
        preset = LayoutPreset.TOWER,
        slots = listOf(GridPos(0f, 0)) +
            row(-3, -0.5f, 0.5f) +                 // 1..2
            row(-2, -1f, 0f, 1f) +                 // 3..5
            row(-1, -0.5f, 0.5f) +                 // 6..7
            row(0, -1f, 1f) +                      // 8..9
            row(1, -0.5f, 0.5f) +                  // 10..11
            row(2, -1f, 0f, 1f) +                  // 12..14
            row(3, -0.5f, 0.5f),                   // 15..16
        fillOrder = listOf(6, 7, 9, 11, 10, 8, 1, 2, 3, 4, 5, 12, 14, 15, 16),
    )

    /** Centre plus two full rings. 19 slots, three start free. */
    val RING = PresetSpec(
        preset = LayoutPreset.RING,
        slots = listOf(GridPos(0f, 0)) +
            row(-2, -1f, 0f, 1f) +                 // 1..3
            row(-1, -1.5f, -0.5f, 0.5f, 1.5f) +    // 4..7
            row(0, -2f, -1f, 1f, 2f) +             // 8..11
            row(1, -1.5f, -0.5f, 0.5f, 1.5f) +     // 12..15
            row(2, -1f, 0f, 1f),                   // 16..18
        fillOrder = listOf(5, 6, 10, 14, 13, 9, 1, 2, 3, 7, 15, 18, 16, 12, 4),
    )

    fun of(preset: LayoutPreset): PresetSpec = when (preset) {
        LayoutPreset.HONEYCOMB -> HONEYCOMB
        LayoutPreset.TOWER -> TOWER
        LayoutPreset.RING -> RING
    }
}
