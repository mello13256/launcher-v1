package com.hexa.core.persist

import com.hexa.core.combo.ComboMatcher
import com.hexa.core.layout.LayoutPresets
import com.hexa.core.model.CENTER_HIVE_ID
import com.hexa.core.model.CENTER_SLOT
import com.hexa.core.model.ComboSettings
import com.hexa.core.model.DefaultConfig
import com.hexa.core.model.HiveConfig
import com.hexa.core.model.LauncherAction
import com.hexa.core.model.LauncherConfig
import com.hexa.core.model.TOTAL_HIVES
import kotlinx.serialization.json.Json

object ConfigCodec {

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
        coerceInputValues = true
    }

    fun encode(config: LauncherConfig): String = json.encodeToString(LauncherConfig.serializer(), config)

    /** Decodes and repairs a config. Throws on malformed JSON so callers can keep a backup. */
    fun decode(text: String): LauncherConfig = sanitize(json.decodeFromString(LauncherConfig.serializer(), text))

    /**
     * Repairs anything that would break the UI: missing/duplicated hives, slots outside the preset,
     * combinations pointing at nonexistent hives, out-of-range numbers. Never drops valid data.
     */
    fun sanitize(config: LauncherConfig): LauncherConfig {
        val spec = LayoutPresets.of(config.layout.preset)
        val defaults = DefaultConfig.hives(config.layout.preset).associateBy { it.id }

        val byId = LinkedHashMap<Int, HiveConfig>()
        for (h in config.hives) if (h.id in 0 until TOTAL_HIVES && h.id !in byId) byId[h.id] = clampHive(h)
        for (id in 0 until TOTAL_HIVES) if (id !in byId) byId[id] = defaults.getValue(id)

        val used = HashSet<Int>().apply { add(CENTER_SLOT) }
        val needsSlot = ArrayList<Int>()
        for (id in 0 until TOTAL_HIVES) {
            val h = byId.getValue(id)
            if (id == CENTER_HIVE_ID) {
                byId[id] = h.copy(slot = CENTER_SLOT)
            } else if (h.slot !in 1 until spec.slotCount || !used.add(h.slot)) {
                needsSlot += id
            }
        }
        if (needsSlot.isNotEmpty()) {
            val free = (spec.fillOrder + (1 until spec.slotCount)).distinct().filter { it !in used }.iterator()
            for (id in needsSlot) byId[id] = byId.getValue(id).copy(slot = free.next())
        }

        val seenIds = HashSet<String>()
        val combos = config.combos.filter { c ->
            c.hives.size in ComboMatcher.MIN_LENGTH..ComboMatcher.MAX_LENGTH &&
                c.hives.all { it in 0 until TOTAL_HIVES } &&
                c.action !is LauncherAction.RunCombo &&
                seenIds.add(c.id)
        }

        val layout = config.layout.let {
            it.copy(
                gap = it.gap.coerceIn(0f, 0.3f),
                centerScale = it.centerScale.coerceIn(1f, 1.4f),
                cornerRadius = it.cornerRadius.coerceIn(0f, 0.35f),
                scale = it.scale.coerceIn(0.6f, 1f),
                verticalBias = it.verticalBias.coerceIn(-1f, 1f),
                maxRadiusDp = it.maxRadiusDp.coerceIn(32f, 160f),
            )
        }
        return config.copy(
            hives = (0 until TOTAL_HIVES).map { byId.getValue(it) },
            combos = combos,
            layout = layout,
            comboSettings = config.comboSettings.copy(
                windowMs = config.comboSettings.windowMs.coerceIn(ComboSettings.MIN_WINDOW_MS, ComboSettings.MAX_WINDOW_MS),
            ),
            animation = config.animation.copy(speed = config.animation.speed.coerceIn(0.5f, 1.5f)),
            background = config.background.copy(dim = config.background.dim.coerceIn(0f, 0.8f)),
            search = config.search.copy(maxResults = config.search.maxResults.coerceIn(3, 30)),
        )
    }

    private fun clampHive(h: HiveConfig) = h.copy(
        opacity = h.opacity.coerceIn(0f, 1f),
        iconScale = h.iconScale.coerceIn(0.3f, 0.9f),
        sizeScale = h.sizeScale.coerceIn(0.6f, 1f),
        outlineWidth = h.outlineWidth.coerceIn(0f, 6f),
        image = h.image?.let { it.copy(zoom = it.zoom.coerceIn(1f, 4f), offsetX = it.offsetX.coerceIn(-1f, 1f), offsetY = it.offsetY.coerceIn(-1f, 1f)) },
    )
}
