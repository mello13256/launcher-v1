package com.hexa.core.layout

import com.hexa.core.model.CENTER_HIVE_ID
import com.hexa.core.model.CENTER_SLOT
import com.hexa.core.model.ComboConfig
import com.hexa.core.model.HiveVisual
import com.hexa.core.model.LauncherAction
import com.hexa.core.model.LauncherConfig
import com.hexa.core.model.LayoutPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangementTest {

    private val base = LauncherConfig()
        .withHive(0) { it.copy(tapAction = LauncherAction.OpenApp("a/.A", "A"), color = 0xFF112233, visual = HiveVisual.COLOR) }
        .withHive(1) { it.copy(tapAction = LauncherAction.OpenApp("b/.B", "B"), opacity = 0.3f) }
        .copy(combos = listOf(ComboConfig("c", listOf(0, 1), action = LauncherAction.OpenSearch)))

    private fun moved(r: Arrangement.MoveResult) = (r as Arrangement.MoveResult.Moved).config

    @Test
    fun moveToFreeSlotKeepsEverythingElse() {
        val free = Arrangement.freeSlots(base).single()
        val after = moved(Arrangement.move(base, 0, free))
        assertEquals(free, after.hive(0).slot)
        assertEquals(base.hive(0).copy(slot = free), after.hive(0))
        assertEquals(base.combos, after.combos)
        assertEquals(listOf(base.hive(0).slot), Arrangement.freeSlots(after))
    }

    @Test
    fun moveOntoOccupiedSlotSwapsPositionsOnly() {
        val target = base.hive(1).slot
        val r = Arrangement.move(base, 0, target) as Arrangement.MoveResult.Moved
        assertEquals(1, r.swappedWith)
        val after = r.config
        assertEquals(target, after.hive(0).slot)
        assertEquals(base.hive(0).slot, after.hive(1).slot)
        assertEquals(base.hive(0).tapAction, after.hive(0).tapAction)
        assertEquals(base.hive(1).opacity, after.hive(1).opacity, 0f)
        assertEquals(base.combos, after.combos)
    }

    @Test
    fun centreHiveAndCentreSlotAreFixed() {
        assertTrue(Arrangement.move(base, CENTER_HIVE_ID, 3) is Arrangement.MoveResult.Rejected)
        assertTrue(Arrangement.move(base, 2, CENTER_SLOT) is Arrangement.MoveResult.Rejected)
        assertTrue(Arrangement.move(base, 2, 99) is Arrangement.MoveResult.Rejected)
    }

    @Test
    fun slotsStayUniqueAfterManyMoves() {
        var c = base
        val spec = LayoutPresets.of(c.layout.preset)
        val rnd = kotlin.random.Random(3)
        repeat(500) {
            val r = Arrangement.move(c, rnd.nextInt(15), rnd.nextInt(1, spec.slotCount))
            if (r is Arrangement.MoveResult.Moved) c = r.config
        }
        assertEquals(16, c.hives.map { it.slot }.toSet().size)
        assertEquals(CENTER_SLOT, c.hive(CENTER_HIVE_ID).slot)
        assertEquals(base.hives.map { it.copy(slot = 0) }, c.hives.map { it.copy(slot = 0) })
    }

    @Test
    fun swapContentsMovesCombosWithContent() {
        val after = Arrangement.swapContents(base, 0, 5)
        assertEquals(base.hive(0).tapAction, after.hive(5).tapAction)
        assertEquals(base.hive(0).slot, after.hive(0).slot)
        assertEquals(listOf(5, 1), after.combos.single().hives)
    }

    @Test
    fun changingPresetKeepsHivesAndConfigs() {
        for (p in LayoutPreset.entries) {
            val after = Arrangement.changePreset(base, p)
            val spec = LayoutPresets.of(p)
            assertEquals(p, after.layout.preset)
            assertEquals(16, after.hives.map { it.slot }.toSet().size)
            assertTrue(after.hives.all { it.slot in 0 until spec.slotCount })
            assertEquals(base.hive(0).tapAction, after.hive(0).tapAction)
            assertEquals(base.combos, after.combos)
        }
    }
}
