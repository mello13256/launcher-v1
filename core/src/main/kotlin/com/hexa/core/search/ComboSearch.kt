package com.hexa.core.search

import com.hexa.core.model.ComboConfig
import com.hexa.core.model.LauncherAction

/**
 * Finds combinations relevant to a query: by the app/action they trigger, by their name, or by the
 * apps assigned to the hives they use. Also surfaces combinations that open an app found in the
 * normal results, so the user learns the faster path.
 */
object ComboSearch {

    data class ComboHit(val combo: ComboConfig, val score: Float)

    fun find(
        query: String,
        combos: List<ComboConfig>,
        actionLabel: (LauncherAction) -> String,
        hiveLabel: (Int) -> String,
        resultKeys: Set<String> = emptySet(),
        limit: Int = 4,
    ): List<ComboHit> {
        val q = normalizeForSearch(query)
        if (q.isEmpty()) return emptyList()
        val hits = ArrayList<ComboHit>()
        for (c in combos) {
            if (!c.enabled) continue
            val target = normalizeForSearch(actionLabel(c.action))
            val name = normalizeForSearch(c.name)
            val score = when {
                target.startsWith(q) -> 900f
                name.isNotEmpty() && name.startsWith(q) -> 850f
                target.split(' ').any { it.startsWith(q) } -> 800f
                (c.action as? LauncherAction.OpenApp)?.appKey in resultKeys -> 700f
                target.contains(q) || (name.isNotEmpty() && name.contains(q)) -> 600f
                c.hives.any { normalizeForSearch(hiveLabel(it)).startsWith(q) } -> 300f
                else -> 0f
            }
            if (score > 0f) hits += ComboHit(c, score - c.hives.size)
        }
        hits.sortByDescending { it.score }
        return hits.take(limit)
    }
}
