package com.hexa.core.model

import com.hexa.core.layout.LayoutPresets

object DefaultConfig {

    /** A dusk palette: readable with white icons, works on dark and light wallpapers. */
    val PALETTE: List<Long> = listOf(
        0xFF3D5AFE, 0xFF7C4DFF, 0xFFD500F9, 0xFFFF4081, 0xFFFF6E40,
        0xFFFFAB00, 0xFF00C853, 0xFF00B8D4, 0xFF2962FF, 0xFF6200EA,
        0xFFC51162, 0xFFDD2C00, 0xFF00BFA5, 0xFF64DD17, 0xFF304FFE,
    )

    const val CENTER_COLOR = 0xFF14151F

    fun hives(preset: LayoutPreset = LayoutPreset.HONEYCOMB): List<HiveConfig> {
        val order = LayoutPresets.of(preset).fillOrder
        val regular = (0 until REGULAR_HIVE_COUNT).map { id ->
            HiveConfig(id = id, slot = order[id], color = PALETTE[id % PALETTE.size], opacity = 0.82f)
        }
        val center = HiveConfig(
            id = CENTER_HIVE_ID,
            slot = CENTER_SLOT,
            color = CENTER_COLOR,
            opacity = 0.9f,
            outlineWidth = 1.5f,
            outlineColor = 0xFFB388FF,
        )
        return regular + center
    }
}
