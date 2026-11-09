package com.victorious.browser.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** WebView Profile name for private tabs (valid [com.victorious.browser.sandbox.SandboxName]). */
const val INCOGNITO_PROFILE = "incognito"

/**
 * One browser tab's metadata.
 *
 * The live [android.webkit.WebView] instance is NOT held here — it lives in the
 * Compose layer (see TabbedBrowserScreen), keyed by [id]. This keeps [TabStore]
 * a pure, Android-free, unit-testable state container while the WebView (and its
 * full live DOM / back-forward history) is preserved by keeping the instance
 * alive across tab switches.
 */
data class BrowserTab(
    val id: String,
    val title: String,
    val url: String,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    /** Private tab: skipped by history + session restore. Shares an isolated WebView profile when available. */
    val incognito: Boolean = false,
)

/** A recently-closed (non-private) tab that can be reopened. */
data class ClosedTab(val url: String, val title: String)

/**
 * StateFlow-based store for the open browser tabs and which one is active.
 *
 * Pure Kotlin (no Android types) so the open/close/activate/ordering and
 * active-tab-invariant logic is covered by JVM unit tests (TabStoreTest).
 *
 * Invariants it maintains:
 *  - [activeTabId] is null iff [tabs] is empty.
 *  - Whenever [tabs] is non-empty, [activeTabId] references an existing tab.
 *  - Closing the active tab selects a sensible neighbour (the next tab, or the
 *    previous one if the closed tab was last).
 *  - Insertion order is preserved; new tabs append to the end.
 */
class TabStore(
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    // Recently-closed stack (most-recent first), for "reopen closed tab". Private
    // (incognito) tabs are never recorded, and the stack is bounded + clearable so
    // a privacy Fire run leaves nothing restorable.
    private val _recentlyClosed = MutableStateFlow<List<ClosedTab>>(emptyList())
    val recentlyClosed: StateFlow<List<ClosedTab>> = _recentlyClosed.asStateFlow()

    /** True when at least one closed tab can be reopened. */
    val hasRecentlyClosed: Boolean get() = _recentlyClosed.value.isNotEmpty()

    /** Current number of open tabs. */
    val count: Int get() = _tabs.value.size

    /** The active tab, or null when there are no tabs. */
    fun activeTab(): BrowserTab? {
        val id = _activeTabId.value ?: return null
        return _tabs.value.firstOrNull { it.id == id }
    }

    fun get(id: String): BrowserTab? = _tabs.value.firstOrNull { it.id == id }

    fun indexOf(id: String): Int = _tabs.value.indexOfFirst { it.id == id }

    /**
     * Open a new tab for [url]. Appends to the end. When [activate] is true (the
     * default) the new tab becomes active. Returns the new tab's id.
     */
    fun openTab(
        url: String,
        title: String = "",
        activate: Boolean = true,
        incognito: Boolean = false,
    ): String {
        val tab = BrowserTab(id = idGenerator(), title = title, url = url, incognito = incognito)
        _tabs.value = _tabs.value + tab
        if (activate || _activeTabId.value == null) {
            _activeTabId.value = tab.id
        }
        return tab.id
    }

    /**
     * Replace the tab list (process-death session restore). [activeId] must refer to
     * a restored tab; otherwise the last tab is selected. Empty [tabs] clears.
     */
    fun restore(tabs: List<BrowserTab>, activeId: String?) {
        _tabs.value = tabs
        _activeTabId.value = when {
            tabs.isEmpty() -> null
            activeId != null && tabs.any { it.id == activeId } -> activeId
            else -> tabs.last().id
        }
    }

    /**
     * Close the tab [id]. If it was the active tab, the next tab becomes active
     * (or the previous one if the closed tab was last). When the last tab is
     * closed, [activeTabId] becomes null.
     */
    fun closeTab(id: String) {
        val current = _tabs.value
        val idx = current.indexOfFirst { it.id == id }
        if (idx < 0) return

        val closed = current[idx]
        val wasActive = _activeTabId.value == id
        val remaining = current.filterNot { it.id == id }
        _tabs.value = remaining

        // Record non-private closes so the user can reopen them (bounded stack).
        if (!closed.incognito && closed.url.isNotBlank()) {
            _recentlyClosed.value =
                (listOf(ClosedTab(closed.url, closed.title)) + _recentlyClosed.value).take(MAX_RECENTLY_CLOSED)
        }

        if (remaining.isEmpty()) {
            _activeTabId.value = null
            return
        }
        if (wasActive) {
            // Prefer the tab that now sits at the closed index (the former "next"
            // tab); clamp to the last tab when the closed one was at the end.
            val newIdx = idx.coerceAtMost(remaining.size - 1)
            _activeTabId.value = remaining[newIdx].id
        }
        // If a non-active tab was closed, the active selection is unchanged and
        // still valid (it was not the removed id).
    }

    /**
     * Ids of every tab except [keepId], in tab order — for "close other tabs".
     * Empty when [keepId] is not an open tab. (The UI closes each so it can also
     * tear down the matching WebView; closing routes through [closeTab], so the
     * recently-closed stack and active-tab invariant stay correct.)
     */
    fun otherTabIds(keepId: String): List<String> =
        if (_tabs.value.any { it.id == keepId }) _tabs.value.filterNot { it.id == keepId }.map { it.id }
        else emptyList()

    /** Ids of the tabs positioned after [id], in order — for "close tabs to the right". */
    fun tabIdsAfter(id: String): List<String> {
        val idx = indexOf(id)
        return if (idx < 0) emptyList() else _tabs.value.drop(idx + 1).map { it.id }
    }

    /** Make [id] the active tab, if it exists. */
    fun activate(id: String) {
        if (_tabs.value.any { it.id == id }) {
            _activeTabId.value = id
        }
    }

    /**
     * Reopen the most-recently-closed tab (if any), making it active. Returns the
     * new tab's id, or null when the stack is empty.
     */
    fun reopenLastClosed(): String? {
        val top = _recentlyClosed.value.firstOrNull() ?: return null
        _recentlyClosed.value = _recentlyClosed.value.drop(1)
        return openTab(top.url, title = top.title, activate = true)
    }

    /** Forget all recently-closed tabs (e.g. after a privacy Fire run). */
    fun clearRecentlyClosed() {
        _recentlyClosed.value = emptyList()
    }

    /** Close every tab. Also forgets recently-closed tabs. */
    fun closeAll() {
        _tabs.value = emptyList()
        _activeTabId.value = null
        _recentlyClosed.value = emptyList()
    }

    fun updateUrl(id: String, url: String) = mutate(id) { it.copy(url = url) }

    fun updateTitle(id: String, title: String) = mutate(id) { it.copy(title = title) }

    fun updateNav(id: String, canGoBack: Boolean, canGoForward: Boolean) =
        mutate(id) { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }

    private inline fun mutate(id: String, transform: (BrowserTab) -> BrowserTab) {
        var changed = false
        val next = _tabs.value.map { t ->
            if (t.id == id) { changed = true; transform(t) } else t
        }
        if (changed) _tabs.value = next
    }

    companion object {
        /** Maximum number of closed tabs retained for reopening. */
        const val MAX_RECENTLY_CLOSED = 10
    }
}
