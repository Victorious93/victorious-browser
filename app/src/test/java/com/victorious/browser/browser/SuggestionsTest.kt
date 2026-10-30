package com.victorious.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsTest {
    private val tabs = listOf(BrowserTab("t1", "GitHub", "https://github.com/"), BrowserTab("t2", "Active", "https://gitlab.com/"))
    private val bms = listOf(Bookmark("https://github.com/", "GH bookmark"), Bookmark("https://docs.gitea.io/", "Gitea docs"))
    private val hist = listOf(
        HistoryEntry("https://example.com/git-guide", "Git guide", 10),
        HistoryEntry("https://gitea.com/", "Gitea", 5),
    )

    @Test fun emptyQueryGivesNothing() {
        assertTrue(buildSuggestions("  ", tabs, bms, hist).isEmpty())
    }

    @Test fun dedupesPreferringOpenTabAndExcludesActive() {
        val s = buildSuggestions("git", tabs, bms, hist, excludeTabId = "t2")
        assertEquals(1, s.count { it.url == "https://github.com/" })
        assertEquals(SuggestionSource.OPEN_TAB, s.first { it.url == "https://github.com/" }.source)
        assertTrue(s.none { it.tabId == "t2" })
    }

    @Test fun hostPrefixRanksAboveSubstring() {
        val s = buildSuggestions("git", tabs, bms, hist, excludeTabId = "t2")
        val guide = s.indexOfFirst { it.url.contains("git-guide") }
        val gitea = s.indexOfFirst { it.url == "https://gitea.com/" }
        assertTrue(gitea in 0 until guide)
    }

    @Test fun matchesTitleAndRespectsLimit() {
        assertEquals("https://docs.gitea.io/", buildSuggestions("docs", emptyList(), bms, emptyList()).single().url)
        assertEquals(2, buildSuggestions("g", tabs, bms, hist, limit = 2).size)
    }
}
