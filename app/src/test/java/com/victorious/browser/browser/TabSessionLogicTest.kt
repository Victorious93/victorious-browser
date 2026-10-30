package com.victorious.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabSessionLogicTest {

    @Test
    fun dropsIncognitoAndNonWebUrls() {
        val tabs = listOf(
            BrowserTab("1", "A", "https://a.example"),
            BrowserTab("2", "Priv", "https://secret.example", incognito = true),
            BrowserTab("3", "Blank", "about:blank"),
            BrowserTab("4", "B", "https://b.example"),
        )
        val snap = TabSessionLogic.snapshot(tabs, activeId = "2")
        assertEquals(listOf("1", "4"), snap.tabs.map { it.id })
        // Active was incognito — fall back to last persistable tab.
        assertEquals("4", snap.activeId)
    }

    @Test
    fun codecRoundTrip() {
        val snap = TabSessionSnapshot(
            listOf(PersistedTab("a", "https://a.example", "Alpha")),
            activeId = "a",
        )
        assertEquals(snap, TabSessionLogic.decode(TabSessionLogic.encode(snap)))
    }

    @Test
    fun decodeMalformedAndEmpty() {
        assertTrue(TabSessionLogic.decode(null).tabs.isEmpty())
        assertTrue(TabSessionLogic.decode("{bad").tabs.isEmpty())
        assertTrue(TabSessionLogic.decode("{\"tabs\":[{\"url\":\"\"}]}").tabs.isEmpty())
    }

    @Test
    fun toBrowserTabsNeverIncognito() {
        val snap = TabSessionSnapshot(listOf(PersistedTab("a", "https://a.example", "A")), "a")
        val (tabs, active) = TabSessionLogic.toBrowserTabs(snap)
        assertEquals("a", active)
        assertFalse(tabs.single().incognito)
        assertEquals("https://a.example", tabs.single().url)
    }

    @Test
    fun persistableRequiresHttpAndNotPrivate() {
        assertTrue(TabSessionLogic.persistable(BrowserTab("1", "", "https://x.test")))
        assertFalse(TabSessionLogic.persistable(BrowserTab("1", "", "https://x.test", incognito = true)))
        assertFalse(TabSessionLogic.persistable(BrowserTab("1", "", "javascript:alert(1)")))
    }

    @Test
    fun capsAtMaxTabs() {
        val tabs = (0 until 30).map { BrowserTab("t$it", "", "https://h$it.example") }
        val snap = TabSessionLogic.snapshot(tabs, "t29")
        assertEquals(TabSessionLogic.MAX_TABS, snap.tabs.size)
        assertEquals("t0", snap.tabs.first().id)
    }

    @Test
    fun decodeDropsUnrecordableUrls() {
        val raw = """{"tabs":[{"id":"a","url":"about:blank","title":""},{"id":"b","url":"https://ok.example","title":"Ok"}],"activeId":"a"}"""
        val snap = TabSessionLogic.decode(raw)
        assertEquals(listOf("b"), snap.tabs.map { it.id })
        assertEquals("b", snap.activeId)
        assertNull(TabSessionLogic.decode("[]").activeId)
    }
}
