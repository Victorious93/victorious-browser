package com.victorious.browser.browser

/** Where an address-bar suggestion came from. */
enum class SuggestionSource { OPEN_TAB, BOOKMARK, HISTORY }

data class Suggestion(
    val url: String,
    val title: String,
    val source: SuggestionSource,
    /** Tab id when [source] is OPEN_TAB (switch instead of navigating). */
    val tabId: String? = null,
)

/**
 * Pure address-bar autocomplete: matches [query] against open tabs, bookmarks and
 * history (URL or title, case-insensitive). Prefix matches on the host rank first,
 * then by source (tab > bookmark > history), then by history recency/visits.
 * De-duplicated by URL (the higher-priority source wins). Unit-tested.
 */
internal fun buildSuggestions(
    query: String,
    tabs: List<BrowserTab>,
    bookmarks: List<Bookmark>,
    history: List<HistoryEntry>,
    excludeTabId: String? = null,
    limit: Int = 8,
): List<Suggestion> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()

    fun matches(url: String, title: String) =
        url.contains(q, ignoreCase = true) || title.contains(q, ignoreCase = true)

    fun prefixRank(url: String): Int {
        val bare = url.substringAfter("://").removePrefix("www.")
        return if (bare.startsWith(q, ignoreCase = true) ||
            url.startsWith(q, ignoreCase = true)
        ) 0 else 1
    }

    val candidates = ArrayList<Pair<Suggestion, Int>>()
    tabs.filter { it.id != excludeTabId && matches(it.url, it.title) }.forEach {
        candidates += Suggestion(it.url, it.title, SuggestionSource.OPEN_TAB, it.id) to 0
    }
    bookmarks.filter { matches(it.url, it.title) }.forEach {
        candidates += Suggestion(it.url, it.title, SuggestionSource.BOOKMARK) to 1
    }
    // History is already newest-first; frequently visited pages get a nudge.
    history.filter { matches(it.url, it.title) }
        .sortedWith(compareByDescending<HistoryEntry> { it.visits >= 3 }.thenByDescending { it.lastVisited })
        .forEach { candidates += Suggestion(it.url, it.title, SuggestionSource.HISTORY) to 2 }

    val seen = HashSet<String>()
    return candidates
        .withIndex()
        .sortedWith(
            compareBy<IndexedValue<Pair<Suggestion, Int>>> { prefixRank(it.value.first.url) }
                .thenBy { it.value.second }
                .thenBy { it.index },
        )
        .map { it.value.first }
        .filter { seen.add(it.url.trimEnd('/')) }
        .take(limit)
}
