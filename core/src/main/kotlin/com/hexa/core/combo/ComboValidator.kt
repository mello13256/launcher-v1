package com.hexa.core.combo

import com.hexa.core.model.ComboConfig
import com.hexa.core.model.LauncherAction
import com.hexa.core.model.TOTAL_HIVES

sealed interface ComboIssue {
    val message: String

    data object TooShort : ComboIssue {
        override val message = "Uma combinação precisa de pelo menos ${ComboMatcher.MIN_LENGTH} toques"
    }

    data object TooLong : ComboIssue {
        override val message = "Máximo de ${ComboMatcher.MAX_LENGTH} toques por combinação"
    }

    data object UnknownHive : ComboIssue {
        override val message = "A combinação refere uma colmeia inexistente"
    }

    data object NoAction : ComboIssue {
        override val message = "Escolhe a aplicação ou ação a executar"
    }

    data object RecursiveAction : ComboIssue {
        override val message = "Uma combinação não pode executar outra combinação"
    }

    /** Same taps would trigger two different combinations. */
    data class Conflict(val otherId: String) : ComboIssue {
        override val message = "Já existe uma combinação com os mesmos toques"
    }
}

/** Non-blocking notes shown in the editor. */
sealed interface ComboWarning {
    val message: String

    /** This combination is the beginning of a longer one, so it fires only when the window closes. */
    data class WaitsForLonger(val longerId: String) : ComboWarning {
        override val message = "Esta combinação é o início de outra mais longa: só dispara quando o intervalo terminar"
    }

    /** A shorter combination is a prefix of this one and will now wait for the window. */
    data class DelaysShorter(val shorterId: String) : ComboWarning {
        override val message = "Uma combinação mais curta passa a aguardar o intervalo completo"
    }
}

object ComboValidator {

    fun errors(candidate: ComboConfig, existing: List<ComboConfig>): List<ComboIssue> {
        val issues = ArrayList<ComboIssue>()
        if (candidate.hives.size < ComboMatcher.MIN_LENGTH) issues += ComboIssue.TooShort
        if (candidate.hives.size > ComboMatcher.MAX_LENGTH) issues += ComboIssue.TooLong
        if (candidate.hives.any { it !in 0 until TOTAL_HIVES }) issues += ComboIssue.UnknownHive
        when (candidate.action) {
            LauncherAction.None -> issues += ComboIssue.NoAction
            is LauncherAction.RunCombo -> issues += ComboIssue.RecursiveAction
            else -> Unit
        }
        existing.firstOrNull { it.id != candidate.id && it.enabled && conflicts(candidate, it) }
            ?.let { issues += ComboIssue.Conflict(it.id) }
        return issues
    }

    fun warnings(candidate: ComboConfig, existing: List<ComboConfig>): List<ComboWarning> {
        val out = ArrayList<ComboWarning>()
        for (other in existing) {
            if (other.id == candidate.id || !other.enabled) continue
            if (isPrefixOf(candidate, other)) out += ComboWarning.WaitsForLonger(other.id)
            else if (isPrefixOf(other, candidate)) out += ComboWarning.DelaysShorter(other.id)
        }
        return out
    }

    /** True when some tap sequence exactly matches both combinations. */
    fun conflicts(a: ComboConfig, b: ComboConfig): Boolean = when {
        a.ordered && b.ordered -> a.hives == b.hives
        else -> a.hives.sorted() == b.hives.sorted()
    }

    /** True when a sequence matching [short] can be continued into one matching [long]. */
    fun isPrefixOf(short: ComboConfig, long: ComboConfig): Boolean {
        if (short.hives.size >= long.hives.size) return false
        val matcher = ComboMatcher(listOf(long.copy(enabled = true)))
        return if (short.ordered) {
            matcher.canExtend(short.hives)
        } else {
            // Any ordering of the short multiset must be able to continue; checking the multiset is enough
            // for unordered long combos, and for ordered long combos some permutation must be a prefix.
            if (!long.ordered) matcher.canExtend(short.hives)
            else long.hives.subList(0, short.hives.size).sorted() == short.hives.sorted()
        }
    }
}
