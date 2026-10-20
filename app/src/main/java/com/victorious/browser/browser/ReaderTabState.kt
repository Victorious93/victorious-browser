package com.victorious.browser.browser

/**
 * Pure per-tab Reader-mode bookkeeping for [TabbedBrowserScreen]: which tabs are
 * showing the reader rendering, and the original article URL to restore on exit.
 * A real navigation to a different URL exits reader mode for that tab (mirrors
 * the single-tab BrowserScreen behaviour).
 */
class ReaderTabState {
    private val articleUrls = mutableMapOf<String, String>()

    fun isActive(tabId: String): Boolean = tabId in articleUrls

    fun enter(tabId: String, articleUrl: String) { articleUrls[tabId] = articleUrl }

    /** Exits reader mode; returns the URL to reload, or null if it wasn't active. */
    fun exit(tabId: String): String? = articleUrls.remove(tabId)

    /** Returns true if this URL change kicked the tab out of reader mode. */
    fun onUrlChanged(tabId: String, url: String): Boolean {
        val article = articleUrls[tabId] ?: return false
        if (url == article) return false
        articleUrls.remove(tabId)
        return true
    }

    /** Forget a closed tab. Returns true if it was in reader mode. */
    fun remove(tabId: String): Boolean = articleUrls.remove(tabId) != null
}
