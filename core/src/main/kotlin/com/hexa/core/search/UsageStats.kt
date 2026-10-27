package com.hexa.core.search

import kotlinx.serialization.Serializable

@Serializable
data class UsageEntry(val count: Int = 0, val lastUsed: Long = 0)

/** Launch counts used to rank search results. Persisted separately from the main config. */
@Serializable
data class UsageStats(val entries: Map<String, UsageEntry> = emptyMap()) {

    fun record(key: String, now: Long): UsageStats {
        val old = entries[key] ?: UsageEntry()
        var next = entries + (key to UsageEntry(old.count + 1, now))
        if (next.size > MAX_ENTRIES) {
            next = next.entries.sortedByDescending { it.value.lastUsed }.take(MAX_ENTRIES).associate { it.key to it.value }
        }
        return UsageStats(next)
    }

    fun boost(key: String, now: Long): Float {
        val e = entries[key] ?: return 0f
        return SearchIndex.usageBoost(e.count, e.lastUsed, now)
    }

    companion object {
        const val MAX_ENTRIES = 400
    }
}
