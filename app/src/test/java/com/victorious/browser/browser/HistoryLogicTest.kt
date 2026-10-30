package com.victorious.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryLogicTest {
    @Test fun recordsNewestFirstAndDedupes() {
        var h = emptyList<HistoryEntry>()
        h = HistoryLogic.record(h, "https://a.com/", "A", 1)
        h = HistoryLogic.record(h, "https://b.com/", "B", 2)
        h = HistoryLogic.record(h, "https://a.com/", "", 3)
        assertEquals(listOf("https://a.com/", "https://b.com/"), h.map { it.url })
        assertEquals(2, h[0].visits)
        assertEquals("A", h[0].title) // blank title keeps previous
        assertEquals(3L, h[0].lastVisited)
    }

    @Test fun skipsNonWebUrls() {
        for (u in listOf("about:blank", "data:text/html,x", "javascript:void(0)", "", "file:///x")) {
            assertFalse(HistoryLogic.isRecordable(u))
            assertTrue(HistoryLogic.record(emptyList(), u, "t", 1).isEmpty())
        }
        assertTrue(HistoryLogic.isRecordable("HTTP://X.COM"))
    }

    @Test fun capsSize() {
        var h = emptyList<HistoryEntry>()
        repeat(10) { h = HistoryLogic.record(h, "https://s$it.com/", "", it.toLong(), max = 5) }
        assertEquals(5, h.size)
        assertEquals("https://s9.com/", h.first().url)
    }

    @Test fun retitleAndSearch() {
        var h = HistoryLogic.record(emptyList(), "https://a.com/x", "", 1)
        h = HistoryLogic.retitle(h, "https://a.com/x", "Hello World")
        assertEquals("Hello World", h[0].title)
        assertEquals(1, HistoryLogic.search(h, "world").size)
        assertEquals(1, HistoryLogic.search(h, "A.COM").size)
        assertEquals(0, HistoryLogic.search(h, "zzz").size)
        assertEquals(1, HistoryLogic.search(h, "  ").size)
    }

    @Test fun codecRoundTripAndMalformed() {
        val h = listOf(HistoryEntry("https://a.com/", "A", 5, 3), HistoryEntry("https://b.com/", "", 6, 1))
        assertEquals(h, HistoryLogic.decode(HistoryLogic.encode(h)))
        assertTrue(HistoryLogic.decode(null).isEmpty())
        assertTrue(HistoryLogic.decode("{bad").isEmpty())
        assertTrue(HistoryLogic.decode("[1,{\"url\":\"\"}]").isEmpty())
    }
}
