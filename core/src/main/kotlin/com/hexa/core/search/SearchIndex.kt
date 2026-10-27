package com.hexa.core.search

import java.text.Normalizer
import kotlin.math.ln
import kotlin.math.min

/** Something that can be found by name: an app, or a launcher function. */
data class SearchItem(
    val key: String,
    val label: String,
    val packageName: String = "",
    /** Extra words that should also match (e.g. "definições" for the settings entry). */
    val keywords: List<String> = emptyList(),
)

data class SearchHit(val item: SearchItem, val score: Float)

/** Lower-case, accents removed, separators collapsed to single spaces. */
fun normalizeForSearch(text: String): String {
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
    val sb = StringBuilder(decomposed.length)
    var lastSpace = true
    for (ch in decomposed) {
        when {
            Character.getType(ch) == Character.NON_SPACING_MARK.toInt() -> Unit
            ch.isLetterOrDigit() -> {
                sb.append(ch.lowercaseChar())
                lastSpace = false
            }
            !lastSpace -> {
                sb.append(' ')
                lastSpace = true
            }
        }
    }
    if (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1)
    return sb.toString()
}

/**
 * In-memory app index. Labels are normalised once when the index is built; each keystroke only
 * scans precomputed strings. When a query extends the previous one, only the previous matches are
 * re-scored (every match rule is monotonic), so typing gets cheaper with every character.
 */
class SearchIndex(items: List<SearchItem>) {

    private class Entry(
        val item: SearchItem,
        val label: String,
        val compact: String,
        val words: List<String>,
        /** Initials of the plain words ("ym" for YouTube Music) and of CamelCase parts ("ytm"). */
        val initials: List<String>,
        val pkg: String,
        val keywords: List<String>,
    )

    private val entries: List<Entry> = items.map { item ->
        val label = normalizeForSearch(item.label)
        val words = splitWords(item.label)
        val plainWords = label.split(' ').filter { it.isNotEmpty() }
        val camelWords = splitCamel(item.label)
        Entry(
            item = item,
            label = label,
            compact = label.replace(" ", ""),
            words = words,
            initials = listOf(plainWords, camelWords).map { w -> w.joinToString("") { it.take(1) } }.distinct(),
            pkg = item.packageName.lowercase(),
            keywords = item.keywords.map(::normalizeForSearch),
        )
    }

    val size: Int get() = entries.size

    private var lastQuery: String? = null
    private var lastMatchPackage = false
    private var lastMatches: List<Entry> = entries

    /**
     * Returns the best [limit] hits for [query]. [usageBoost] adds a ranking bonus (e.g. from launch
     * frequency); it never makes a non-matching item appear.
     */
    fun search(
        query: String,
        limit: Int = 12,
        matchPackage: Boolean = false,
        usageBoost: (String) -> Float = { 0f },
    ): List<SearchHit> {
        val q = normalizeForSearch(query)
        if (q.isEmpty()) {
            lastQuery = null
            lastMatches = entries
            return emptyList()
        }
        val previous = lastQuery
        val pool = if (previous != null && matchPackage == lastMatchPackage && q.startsWith(previous)) lastMatches else entries

        val matched = ArrayList<Entry>()
        val hits = ArrayList<SearchHit>()
        for (e in pool) {
            val base = score(e, q, matchPackage)
            if (base <= 0f) continue
            matched += e
            hits += SearchHit(e.item, base + usageBoost(e.item.key))
        }
        lastQuery = q
        lastMatchPackage = matchPackage
        lastMatches = matched

        hits.sortWith(compareByDescending<SearchHit> { it.score }.thenBy { it.item.label.length }.thenBy { it.item.label.lowercase() })
        return if (hits.size > limit) hits.subList(0, limit).toList() else hits
    }

    /** Forget the incremental state (call when the index content or settings change). */
    fun reset() {
        lastQuery = null
        lastMatches = entries
    }

    private fun score(e: Entry, q: String, matchPackage: Boolean): Float {
        val label = e.label
        if (label == q) return 1000f
        if (label.startsWith(q)) return 900f - min(50, label.length - q.length)
        val qCompact = q.replace(" ", "")
        for ((i, w) in e.words.withIndex()) {
            if (w.startsWith(q)) return 800f - i * 10f - min(40, w.length - q.length)
        }
        if (qCompact.length >= 2) {
            for (ini in e.initials) if (ini.startsWith(qCompact)) return 720f - (ini.length - qCompact.length) * 5f
        }
        val idx = label.indexOf(q)
        if (idx >= 0) return 600f - min(100, idx * 5)
        if (qCompact.length >= 2 && e.compact.indexOf(qCompact) >= 0) return 560f
        for (k in e.keywords) if (k.startsWith(q)) return 500f
        if (matchPackage && e.pkg.indexOf(qCompact) >= 0) return 400f
        if (qCompact.length >= 2) {
            val gaps = subsequenceGaps(e.compact, qCompact)
            if (gaps >= 0) return (300f - gaps * 8f).coerceAtLeast(60f)
        }
        return 0f
    }

    companion object {
        /** Plain words plus CamelCase parts: "WhatsApp Business" -> [whatsapp, business, whats, app]. */
        internal fun splitWords(label: String): List<String> {
            val plain = normalizeForSearch(label).split(' ').filter { it.isNotEmpty() }
            return (plain + splitCamel(label)).distinct()
        }

        /** Splits CamelCase and separators: "WhatsApp Business" -> [whats, app, business]. */
        internal fun splitCamel(label: String): List<String> {
            val words = ArrayList<String>()
            val current = StringBuilder()
            var prevLower = false
            val norm = Normalizer.normalize(label, Normalizer.Form.NFD)
            for (ch in norm) {
                if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) continue
                if (!ch.isLetterOrDigit()) {
                    if (current.isNotEmpty()) words += current.toString()
                    current.clear()
                    prevLower = false
                    continue
                }
                if (ch.isUpperCase() && prevLower && current.isNotEmpty()) {
                    words += current.toString()
                    current.clear()
                }
                current.append(ch.lowercaseChar())
                prevLower = ch.isLowerCase() || ch.isDigit()
            }
            if (current.isNotEmpty()) words += current.toString()
            return words
        }

        /** Number of skipped characters for a subsequence match, or -1. */
        internal fun subsequenceGaps(text: String, q: String): Int {
            var ti = 0
            var gaps = 0
            var started = false
            for (c in q) {
                var found = false
                while (ti < text.length) {
                    val t = text[ti++]
                    if (t == c) {
                        found = true
                        started = true
                        break
                    }
                    if (started) gaps++
                }
                if (!found) return -1
            }
            return gaps
        }

        /** Usage ranking bonus: grows with the log of launches and decays with time since last use. */
        fun usageBoost(count: Int, lastUsedMs: Long, nowMs: Long): Float {
            if (count <= 0) return 0f
            val freq = (ln(1.0 + count) * 28.0).toFloat()
            val ageHours = ((nowMs - lastUsedMs).coerceAtLeast(0L) / 3_600_000.0)
            val recency = (40.0 / (1.0 + ageHours / 12.0)).toFloat()
            return min(150f, freq + recency)
        }
    }
}
