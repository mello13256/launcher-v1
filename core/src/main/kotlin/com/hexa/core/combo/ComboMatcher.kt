package com.hexa.core.combo

import com.hexa.core.model.ComboConfig

/**
 * Precomputed lookup tables for combination matching. Every query is a hash lookup, so tap
 * handling cost does not grow with the number of combinations.
 *
 * Ordered combinations match the exact sequence; unordered ones match the same multiset of hives
 * (so a hive may appear twice, e.g. a double tap).
 */
class ComboMatcher(combos: List<ComboConfig>) {

    private val exactOrdered = HashMap<List<Int>, ComboConfig>()
    private val exactUnordered = HashMap<List<Int>, ComboConfig>()
    private val orderedPrefixes = HashSet<List<Int>>()
    private val unorderedSubsets = HashSet<List<Int>>()
    private val participants = HashSet<Int>()

    val combos: List<ComboConfig> = combos.filter { it.enabled && it.hives.size >= MIN_LENGTH }

    init {
        for (c in this.combos) {
            participants += c.hives
            if (c.ordered) {
                exactOrdered.putIfAbsent(c.hives, c)
                for (n in 1 until c.hives.size) orderedPrefixes += c.hives.subList(0, n).toList()
            } else {
                val sorted = c.hives.sorted()
                exactUnordered.putIfAbsent(sorted, c)
                properSubMultisets(sorted).forEach { unorderedSubsets += it }
            }
        }
    }

    val isEmpty: Boolean get() = combos.isEmpty()

    /** True when [hive] appears in any enabled combination (UI hints; waiting is decided by [canStart]). */
    fun participates(hive: Int): Boolean = hive in participants

    /** True when a combination can begin with [hive] (first of an ordered one, any member of an unordered one). */
    fun canStart(hive: Int): Boolean = canExtend(listOf(hive))

    fun exact(sequence: List<Int>): ComboConfig? =
        exactOrdered[sequence] ?: exactUnordered[sequence.sorted()]

    /** True when more taps after [sequence] could still complete a longer combination. */
    fun canExtend(sequence: List<Int>): Boolean =
        sequence in orderedPrefixes || sequence.sorted() in unorderedSubsets

    companion object {
        const val MIN_LENGTH = 2
        const val MAX_LENGTH = 6

        /** All non-empty proper sub-multisets of a sorted list, each sorted. At most 2^6 entries. */
        internal fun properSubMultisets(sorted: List<Int>): Set<List<Int>> {
            val out = HashSet<List<Int>>()
            val n = sorted.size
            for (mask in 1 until (1 shl n) - 1) {
                val sub = ArrayList<Int>(n)
                for (i in 0 until n) if (mask and (1 shl i) != 0) sub += sorted[i]
                out += sub
            }
            return out
        }
    }
}
