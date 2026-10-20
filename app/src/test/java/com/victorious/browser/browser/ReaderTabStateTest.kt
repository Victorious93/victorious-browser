package com.victorious.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTabStateTest {
    @Test fun enterIsPerTab() {
        val s = ReaderTabState()
        s.enter("a", "https://x.com/post")
        assertTrue(s.isActive("a"))
        assertFalse(s.isActive("b"))
    }

    @Test fun sameUrlKeepsReader() {
        val s = ReaderTabState()
        s.enter("a", "https://x.com/post")
        assertFalse(s.onUrlChanged("a", "https://x.com/post"))
        assertTrue(s.isActive("a"))
    }

    @Test fun navigationAwayExits() {
        val s = ReaderTabState()
        s.enter("a", "https://x.com/post")
        assertTrue(s.onUrlChanged("a", "https://x.com/other"))
        assertFalse(s.isActive("a"))
        assertFalse(s.onUrlChanged("b", "https://y.com"))
    }

    @Test fun exitReturnsArticleUrl() {
        val s = ReaderTabState()
        s.enter("a", "https://x.com/post")
        assertEquals("https://x.com/post", s.exit("a"))
        assertNull(s.exit("a"))
    }

    @Test fun removeClosedTab() {
        val s = ReaderTabState()
        s.enter("a", "u")
        assertTrue(s.remove("a"))
        assertFalse(s.remove("a"))
    }
}
