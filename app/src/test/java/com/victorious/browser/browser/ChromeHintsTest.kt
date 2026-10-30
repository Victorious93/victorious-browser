package com.victorious.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeHintsTest {
    @Test fun tlsHints() {
        assertTrue(tlsHint("http://a.com", null, false)!!.warn)
        assertNull(tlsHint("https://a.com", null, false))
        assertEquals("TLS · R3", tlsHint("https://a.com", "R3", false)!!.label)
        assertTrue(tlsHint("https://a.com", "Victorious CA", true)!!.label.startsWith("TLS·proxy"))
        assertNull(tlsHint("about:blank", "x", false))
    }

    @Test fun searchEngineAndNormalize() {
        assertEquals(SearchEngine.DEFAULT, SearchEngine.fromName(null))
        assertEquals(SearchEngine.GOOGLE, SearchEngine.fromName("GOOGLE"))
        assertEquals(SearchEngine.DEFAULT, SearchEngine.fromName("bogus"))
        assertEquals(
            "https://www.google.com/search?q=hello+world",
            normalizeUrl("hello world", SearchEngine.GOOGLE.queryPrefix),
        )
        assertEquals("https://example.com", normalizeUrl("example.com", SearchEngine.GOOGLE.queryPrefix))
        assertTrue(normalizeUrl("pizza").startsWith(SEARCH_URL))
    }
}
