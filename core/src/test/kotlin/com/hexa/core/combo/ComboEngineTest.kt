package com.hexa.core.combo

import com.hexa.core.model.ComboConfig
import com.hexa.core.model.ComboInput
import com.hexa.core.model.ComboSettings
import com.hexa.core.model.LauncherAction
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val A = 0
private const val B = 1
private const val C = 2
private const val D = 3
private const val X = 9 // never part of a combination

class ComboEngineTest {

    private val discord = LauncherAction.OpenApp("com.discord/.Main", "Discord")
    private val spotify = LauncherAction.OpenApp("com.spotify/.Main", "Spotify")
    private val maps = LauncherAction.OpenApp("com.maps/.Main", "Maps")

    private fun combo(id: String, vararg hives: Int, ordered: Boolean = false, action: LauncherAction = discord) =
        ComboConfig(id = id, hives = hives.toList(), ordered = ordered, action = action)

    private class Harness(scope: TestScope, combos: List<ComboConfig>, settings: ComboSettings) {
        val out = mutableListOf<Resolution>()
        val engine = ComboEngine(scope, { scope.testScheduler.currentTime }) { out += it }.apply {
            configure(combos, settings)
        }
    }

    private fun TestScope.harness(vararg combos: ComboConfig, window: Int = 300, input: ComboInput = ComboInput.BOTH) =
        Harness(this, combos.toList(), ComboSettings(windowMs = window, input = input))

    private val TestScope.t: Long get() = testScheduler.currentTime

    @Test
    fun tapOnHiveOutsideCombosResolvesImmediately() = runTest {
        val h = harness(combo("ab", A, B))
        h.engine.onTap(X, t)
        assertEquals(1, h.out.size)
        val r = h.out.single() as Resolution.Single
        assertEquals(X, r.hive)
        assertFalse(r.waited)
        assertEquals(0L, r.resolvedAt - r.eventTime)
    }

    @Test
    fun withoutCombosNothingEverWaits() = runTest {
        val h = harness()
        for (hive in 0 until 16) h.engine.onTap(hive, t)
        assertEquals(16, h.out.size)
        assertTrue(h.out.all { it is Resolution.Single && !it.waited })
    }

    @Test
    fun participatingHiveWaitsForWindowThenOpensSingle() = runTest {
        val h = harness(combo("ab", A, B), window = 250)
        h.engine.onTap(A, t)
        assertEquals(listOf(A), h.engine.pending.value)
        advanceTimeBy(249); runCurrent()
        assertTrue("must not resolve before the window", h.out.isEmpty())
        advanceTimeBy(1); runCurrent()
        val r = h.out.single() as Resolution.Single
        assertEquals(A, r.hive)
        assertTrue(r.waited)
        assertEquals(250L, r.resolvedAt - r.eventTime)
        assertTrue(h.engine.pending.value.isEmpty())
    }

    @Test
    fun unorderedComboFiresOnLastTapWithoutWaitingAndNeverOpensSingle() = runTest {
        val h = harness(combo("ab", A, B))
        h.engine.onTap(A, t)
        advanceTimeBy(120); runCurrent()
        h.engine.onTap(B, t)
        val r = h.out.single() as Resolution.Combo
        assertEquals("ab", r.combo.id)
        assertFalse(r.waited)
        assertEquals(0L, r.resolvedAt - r.eventTime)
        advanceTimeBy(5_000); runCurrent()
        assertEquals("pending single must be cancelled", 1, h.out.size)
    }

    @Test
    fun unorderedComboAcceptsAnyOrder() = runTest {
        val h = harness(combo("ab", A, B, action = discord), combo("cd", C, D, action = spotify))
        h.engine.onTap(B, t); h.engine.onTap(A, t)
        h.engine.onTap(D, t); h.engine.onTap(C, t)
        assertEquals(listOf("ab", "cd"), h.out.map { (it as Resolution.Combo).combo.id })
    }

    @Test
    fun orderedComboRequiresOrder() = runTest {
        val h = harness(combo("ab", A, B, ordered = true))
        // B cannot start the ordered combination, so it opens right away.
        h.engine.onTap(B, t)
        assertTrue((h.out.single() as Resolution.Single).hive == B)
        h.out.clear()

        h.engine.onTap(A, t)
        h.engine.onTap(B, t)
        assertEquals("ab", (h.out.single() as Resolution.Combo).combo.id)
    }

    @Test
    fun orderedSequencesWithSameHivesAreDistinct() = runTest {
        val h = harness(combo("ab", A, B, ordered = true, action = discord), combo("ba", B, A, ordered = true, action = spotify))
        h.engine.onTap(A, t); h.engine.onTap(B, t)
        h.engine.onTap(B, t); h.engine.onTap(A, t)
        assertEquals(listOf(discord, spotify), h.out.map { (it as Resolution.Combo).combo.action })
    }

    @Test
    fun threeHiveCombo() = runTest {
        val h = harness(combo("abc", A, B, C, action = maps))
        h.engine.onTap(C, t); h.engine.onTap(A, t)
        assertTrue(h.out.isEmpty())
        h.engine.onTap(B, t)
        assertEquals(maps, (h.out.single() as Resolution.Combo).combo.action)
    }

    @Test
    fun wrongSecondTapIsInvalidAndOpensNothing() = runTest {
        val h = harness(combo("ab", A, B))
        h.engine.onTap(A, t)
        h.engine.onTap(X, t)
        val r = h.out.single() as Resolution.Invalid
        assertEquals(Resolution.Invalid.Reason.NO_MATCH, r.reason)
        assertEquals(listOf(A, X), r.sequence)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(1, h.out.size)
    }

    @Test
    fun incompleteSequenceExpiresWithoutAction() = runTest {
        val h = harness(combo("abc", A, B, C), window = 300)
        h.engine.onTap(A, t)
        advanceTimeBy(200); runCurrent()
        h.engine.onTap(B, t)
        advanceTimeBy(299); runCurrent()
        assertTrue(h.out.isEmpty())
        advanceTimeBy(1); runCurrent()
        val r = h.out.single() as Resolution.Invalid
        assertEquals(Resolution.Invalid.Reason.INCOMPLETE, r.reason)
        assertEquals(listOf(A, B), r.sequence)
    }

    @Test
    fun windowIsMeasuredBetweenTaps() = runTest {
        val h = harness(combo("ab", A, B), window = 300)
        h.engine.onTap(A, t)
        advanceTimeBy(301); runCurrent()
        h.engine.onTap(B, t)
        // A expired as a single tap; B starts a new pending sequence.
        assertEquals(A, (h.out.single() as Resolution.Single).hive)
        advanceTimeBy(300); runCurrent()
        assertEquals(B, (h.out[1] as Resolution.Single).hive)
    }

    @Test
    fun windowSettingChangesRecognition() = runTest {
        val short = harness(combo("ab", A, B), window = 150)
        short.engine.onTap(A, t)
        advanceTimeBy(200); runCurrent()
        short.engine.onTap(B, t)
        assertTrue(short.out.first() is Resolution.Single)

        val long = harness(combo("ab", A, B), window = 600)
        long.engine.onTap(A, t)
        advanceTimeBy(500); runCurrent()
        long.engine.onTap(B, t)
        assertTrue(long.out.single() is Resolution.Combo)
    }

    @Test
    fun windowIsClampedToSupportedRange() = runTest {
        assertEquals(ComboSettings.MIN_WINDOW_MS.toLong(), harness(window = 5).engine.windowMs)
        assertEquals(ComboSettings.MAX_WINDOW_MS.toLong(), harness(window = 99_999).engine.windowMs)
    }

    @Test
    fun shorterComboThatPrefixesLongerOneWaitsThenFires() = runTest {
        val h = harness(combo("ab", A, B, ordered = true, action = discord), combo("abc", A, B, C, ordered = true, action = maps))
        h.engine.onTap(A, t); h.engine.onTap(B, t)
        assertTrue(h.out.isEmpty())
        advanceTimeBy(300); runCurrent()
        val r = h.out.single() as Resolution.Combo
        assertEquals("ab", r.combo.id)
        assertTrue(r.waited)

        h.out.clear()
        h.engine.onTap(A, t); h.engine.onTap(B, t); h.engine.onTap(C, t)
        assertEquals("abc", (h.out.single() as Resolution.Combo).combo.id)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, h.out.size)
    }

    @Test
    fun doubleTapOnSameHiveCanBeACombo() = runTest {
        val h = harness(combo("aa", A, A, ordered = true, action = spotify))
        h.engine.onTap(A, t); h.engine.onTap(A, t)
        assertEquals(spotify, (h.out.single() as Resolution.Combo).combo.action)
    }

    @Test
    fun lateTimerIsResolvedBeforeNextTap() = runTest {
        val h = harness(combo("ab", A, B), window = 300)
        h.engine.onTap(A, eventTime = 0)
        // The scheduler has not run the timer, but the next event is far outside the window.
        h.engine.onTap(B, eventTime = 1_000)
        assertEquals(A, (h.out.first() as Resolution.Single).hive)
        assertEquals(listOf(B), h.engine.pending.value)
    }

    @Test
    fun cancelPendingDropsSequence() = runTest {
        val h = harness(combo("ab", A, B))
        h.engine.onTap(A, t)
        h.engine.cancelPending()
        assertEquals(Resolution.Invalid.Reason.CANCELLED, (h.out.single() as Resolution.Invalid).reason)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(1, h.out.size)
    }

    @Test
    fun reconfiguringCancelsPending() = runTest {
        val h = harness(combo("ab", A, B))
        h.engine.onTap(A, t)
        h.engine.configure(emptyList(), ComboSettings())
        advanceTimeBy(5_000); runCurrent()
        assertTrue(h.out.single() is Resolution.Invalid)
    }

    @Test
    fun swipeMatchesWithoutWaiting() = runTest {
        val h = harness(combo("abc", A, B, C, ordered = true, action = maps))
        h.engine.onSwipe(listOf(A, A, B, B, C), t)
        val r = h.out.single() as Resolution.Combo
        assertTrue(r.viaSwipe)
        assertEquals(listOf(A, B, C), r.sequence)
        assertFalse(r.waited)
    }

    @Test
    fun swipeWithWrongPathOpensNothing() = runTest {
        val h = harness(combo("ab", A, B))
        h.engine.onSwipe(listOf(A, C), t)
        assertEquals(Resolution.Invalid.Reason.NO_MATCH, (h.out.single() as Resolution.Invalid).reason)
    }

    @Test
    fun swipeOnlyModeNeverDelaysTaps() = runTest {
        val h = harness(combo("ab", A, B), input = ComboInput.SWIPE)
        h.engine.onTap(A, t)
        assertTrue((h.out.single() as Resolution.Single).let { it.hive == A && !it.waited })
        h.engine.onSwipe(listOf(A, B), t)
        assertTrue(h.out[1] is Resolution.Combo)
    }

    @Test
    fun tapsOnlyModeIgnoresSlides() = runTest {
        val h = harness(combo("ab", A, B), input = ComboInput.TAPS)
        h.engine.onSwipe(listOf(A, B), t)
        assertTrue(h.out.isEmpty())
        h.engine.onSwipe(listOf(X), t)
        assertEquals(X, (h.out.single() as Resolution.Single).hive)
    }

    @Test
    fun disabledCombosAreIgnored() = runTest {
        val h = harness(combo("ab", A, B).copy(enabled = false))
        h.engine.onTap(A, t)
        assertTrue(h.out.single() is Resolution.Single)
    }

    @Test
    fun exactlyOneResolutionPerInteraction() = runTest {
        val h = harness(combo("ab", A, B), combo("abc", A, B, C, action = maps), combo("cd", C, D, action = spotify))
        val script = listOf(A, B, C, X, C, D, A, B)
        for (hive in script) {
            h.engine.onTap(hive, t)
            advanceTimeBy(50); runCurrent()
        }
        advanceTimeBy(2_000); runCurrent()
        // A,B,C -> abc ; X -> single ; C,D -> cd ; A,B -> waits -> ab
        assertEquals(
            listOf("combo:abc", "single:9", "combo:cd", "combo:ab"),
            h.out.map {
                when (it) {
                    is Resolution.Combo -> "combo:${it.combo.id}"
                    is Resolution.Single -> "single:${it.hive}"
                    is Resolution.Invalid -> "invalid"
                }
            },
        )
    }
}
