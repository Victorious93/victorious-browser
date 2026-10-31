package com.victorious.browser.browser.privacy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One tracker request seen on a page (blocked, or just observed when blocking is off / site allowed). */
data class TrackerHit(val url: String, val host: String, val category: TrackerCategory, val blocked: Boolean)

/** Per-page dashboard numbers. Reset when a tab starts a new top-level page. */
data class PageStats(
    val pageUrl: String = "",
    val trackers: List<TrackerHit> = emptyList(),
    val httpsUpgrades: Int = 0,
    val paramsStripped: Int = 0,
    val cookiePopupsHandled: Int = 0,
) {
    val blockedCount: Int get() = trackers.count { it.blocked }
    val observedCount: Int get() = trackers.size
    fun byCategory(): Map<TrackerCategory, Int> = trackers.groupingBy { it.category }.eachCount()
    fun companies(): List<String> = trackers.map { HostUtil.baseDomain(it.host) }.distinct()
}

/** In-memory (never persisted) privacy counters keyed by tab id, plus session totals. */
object PrivacyStats {
    private const val MAX_HITS = 300
    private val _pages = MutableStateFlow<Map<String, PageStats>>(emptyMap())
    val pages: StateFlow<Map<String, PageStats>> = _pages.asStateFlow()

    private val _sessionBlocked = MutableStateFlow(0)
    val sessionBlocked: StateFlow<Int> = _sessionBlocked.asStateFlow()

    // Rewrites happen before onPageStarted resets the page, so they wait here.
    private val pending = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Int>>()

    fun newPage(tabId: String, url: String) {
        val (https, strip) = pending.remove(tabId) ?: (0 to 0)
        _pages.update { it + (tabId to PageStats(pageUrl = url, httpsUpgrades = https, paramsStripped = strip)) }
    }

    fun tracker(tabId: String, hit: TrackerHit) {
        if (hit.blocked) _sessionBlocked.update { it + 1 }
        mutate(tabId) { s -> if (s.trackers.size >= MAX_HITS) s else s.copy(trackers = s.trackers + hit) }
    }

    /** Record a top-level rewrite; shows up on the page that the rewrite loads. */
    fun navRewrite(tabId: String, httpsUpgraded: Boolean, stripped: Boolean) {
        if (!httpsUpgraded && !stripped) return
        pending.merge(tabId, (if (httpsUpgraded) 1 else 0) to (if (stripped) 1 else 0)) { a, b ->
            (a.first + b.first) to (a.second + b.second)
        }
    }
    fun cookiePopup(tabId: String) = mutate(tabId) { it.copy(cookiePopupsHandled = it.cookiePopupsHandled + 1) }
    fun remove(tabId: String) { pending.remove(tabId); _pages.update { it - tabId } }
    fun clearAll() { pending.clear(); _pages.value = emptyMap(); _sessionBlocked.value = 0 }

    private fun mutate(tabId: String, f: (PageStats) -> PageStats) =
        _pages.update { m -> m + (tabId to f(m[tabId] ?: PageStats())) }
}
