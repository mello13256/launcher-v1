package com.hexa.core.combo

import com.hexa.core.model.ComboConfig
import com.hexa.core.model.LauncherAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComboValidatorTest {

    private val app = LauncherAction.OpenApp("p/.A", "A")

    private fun c(id: String, vararg hives: Int, ordered: Boolean = false, action: LauncherAction = app) =
        ComboConfig(id, hives.toList(), ordered, action)

    @Test
    fun rejectsTooShortTooLongAndUnknownHives() {
        assertTrue(ComboIssue.TooShort in ComboValidator.errors(c("x", 1), emptyList()))
        assertTrue(ComboIssue.TooLong in ComboValidator.errors(c("x", 1, 2, 3, 4, 5, 6, 7), emptyList()))
        assertTrue(ComboIssue.UnknownHive in ComboValidator.errors(c("x", 1, 42), emptyList()))
    }

    @Test
    fun rejectsMissingAndRecursiveActions() {
        assertTrue(ComboIssue.NoAction in ComboValidator.errors(c("x", 1, 2, action = LauncherAction.None), emptyList()))
        assertTrue(ComboIssue.RecursiveAction in ComboValidator.errors(c("x", 1, 2, action = LauncherAction.RunCombo("y")), emptyList()))
    }

    @Test
    fun rejectsAmbiguousCombinations() {
        val existing = listOf(c("ab", 1, 2))
        // Same set in another order: unordered matches both.
        assertTrue(ComboValidator.errors(c("ba", 2, 1), existing).any { it is ComboIssue.Conflict })
        // Ordered with the same hives also collides with the unordered one.
        assertTrue(ComboValidator.errors(c("ab2", 1, 2, ordered = true), existing).any { it is ComboIssue.Conflict })
    }

    @Test
    fun orderedSequencesInDifferentOrderAreAllowed() {
        val existing = listOf(c("ab", 1, 2, ordered = true))
        assertTrue(ComboValidator.errors(c("ba", 2, 1, ordered = true), existing).isEmpty())
        assertFalse(ComboValidator.errors(c("ab-again", 1, 2, ordered = true), existing).isEmpty())
    }

    @Test
    fun editingAComboDoesNotConflictWithItself() {
        val existing = listOf(c("ab", 1, 2))
        assertTrue(ComboValidator.errors(c("ab", 2, 1, action = LauncherAction.OpenSearch), existing).isEmpty())
    }

    @Test
    fun disabledCombosDoNotBlock() {
        val existing = listOf(c("ab", 1, 2).copy(enabled = false))
        assertTrue(ComboValidator.errors(c("new", 1, 2), existing).isEmpty())
    }

    @Test
    fun warnsAboutPrefixDelays() {
        val existing = listOf(c("abc", 1, 2, 3, ordered = true))
        val w = ComboValidator.warnings(c("ab", 1, 2, ordered = true), existing)
        assertEquals(listOf(ComboWarning.WaitsForLonger("abc")), w)
        val w2 = ComboValidator.warnings(c("abcd", 1, 2, 3, 4, ordered = true), existing)
        assertEquals(listOf(ComboWarning.DelaysShorter("abc")), w2)
        assertTrue(ComboValidator.warnings(c("ba", 2, 1, ordered = true), existing).isEmpty())
    }

    @Test
    fun matcherSubMultisetsHandleRepeats() {
        val subs = ComboMatcher.properSubMultisets(listOf(1, 1, 2))
        assertEquals(setOf(listOf(1), listOf(2), listOf(1, 1), listOf(1, 2)), subs)
    }

    @Test
    fun matcherCanStartFollowsOrdering() {
        val m = ComboMatcher(listOf(c("ab", 1, 2, ordered = true), c("cd", 3, 4)))
        assertTrue(m.canStart(1))
        assertFalse(m.canStart(2))
        assertTrue(m.canStart(3))
        assertTrue(m.canStart(4))
        assertFalse(m.canStart(5))
    }
}
