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

        val wasActive = _activeTabId.value == id
        val remaining = current.filterNot { it.id == id }
        _tabs.value = remaining

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

    /** Make [id] the active tab, if it exists. */
    fun activate(id: String) {
        if (_tabs.value.any { it.id == id }) {
            _activeTabId.value = id
        }
    }

    /** Close every tab. */
    fun closeAll() {
        _tabs.value = emptyList()
        _activeTabId.value = null
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
}
