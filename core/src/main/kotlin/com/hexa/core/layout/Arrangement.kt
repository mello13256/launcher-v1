package com.hexa.core.layout

import com.hexa.core.model.CENTER_HIVE_ID
import com.hexa.core.model.CENTER_SLOT
import com.hexa.core.model.LauncherConfig
import com.hexa.core.model.LayoutPreset

/**
 * Re-arranging only ever changes `HiveConfig.slot`. Apps, colours, images and combinations are
 * attached to the hive id, so they travel with the hive.
 */
object Arrangement {

    sealed interface MoveResult {
        data class Moved(val config: LauncherConfig, val swappedWith: Int?) : MoveResult
        data class Rejected(val reason: String) : MoveResult
    }

    fun move(config: LauncherConfig, hiveId: Int, targetSlot: Int): MoveResult {
        val spec = LayoutPresets.of(config.layout.preset)
        if (hiveId == CENTER_HIVE_ID) return MoveResult.Rejected("A colmeia central fica sempre no centro")
        if (targetSlot == CENTER_SLOT) return MoveResult.Rejected("O centro está reservado à colmeia central")
        if (targetSlot !in 0 until spec.slotCount) return MoveResult.Rejected("Posição inválida")
        val hive = config.hives.firstOrNull { it.id == hiveId } ?: return MoveResult.Rejected("Colmeia inexistente")
        if (hive.slot == targetSlot) return MoveResult.Moved(config, null)

        val occupant = config.hiveAtSlot(targetSlot)
        val hives = config.hives.map {
            when (it.id) {
                hiveId -> it.copy(slot = targetSlot)
                occupant?.id -> it.copy(slot = hive.slot)
                else -> it
            }
        }
        return MoveResult.Moved(config.copy(hives = hives), occupant?.id)
    }

    /** Swaps everything except the slot between two hives ("trocar conteúdo"). Combos follow the content. */
    fun swapContents(config: LauncherConfig, a: Int, b: Int): LauncherConfig {
        if (a == b) return config
        val ha = config.hive(a)
        val hb = config.hive(b)
        val hives = config.hives.map {
            when (it.id) {
                a -> hb.copy(id = a, slot = ha.slot)
                b -> ha.copy(id = b, slot = hb.slot)
                else -> it
            }
        }
        val combos = config.combos.map { c ->
            c.copy(hives = c.hives.map { h -> if (h == a) b else if (h == b) a else h })
        }
        return config.copy(hives = hives, combos = combos)
    }

    /**
     * Switches preset keeping each hive's slot when it exists in the new preset; hives that fall
     * outside take the first free slots of the new preset's fill order.
     */
    fun changePreset(config: LauncherConfig, preset: LayoutPreset): LauncherConfig {
        val spec = LayoutPresets.of(preset)
        val used = HashSet<Int>()
        used += CENTER_SLOT
        val keep = HashMap<Int, Int>()
        for (h in config.hives.sortedBy { it.id }) {
            if (h.id == CENTER_HIVE_ID) continue
            if (h.slot in 1 until spec.slotCount && used.add(h.slot)) keep[h.id] = h.slot
        }
        val free = (spec.fillOrder + (1 until spec.slotCount)).distinct().filter { it !in used }.iterator()
        val hives = config.hives.map { h ->
            when {
                h.id == CENTER_HIVE_ID -> h.copy(slot = CENTER_SLOT)
                keep.containsKey(h.id) -> h.copy(slot = keep.getValue(h.id))
                else -> h.copy(slot = free.next())
            }
        }
        return config.copy(hives = hives, layout = config.layout.copy(preset = preset))
    }

    fun freeSlots(config: LauncherConfig): List<Int> {
        val spec = LayoutPresets.of(config.layout.preset)
        val used = config.hives.map { it.slot }.toSet()
        return (1 until spec.slotCount).filter { it !in used }
    }
}
