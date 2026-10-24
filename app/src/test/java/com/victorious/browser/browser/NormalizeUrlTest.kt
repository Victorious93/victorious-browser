package com.victorious.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Search or enter address" behavior for the browser address bar. */
class NormalizeUrlTest {
    @Test
    fun keepsExplicitSchemes() {
        assertEquals("https://example.com", normalizeUrl("https://example.com"))
        assertEquals("http://example.com", normalizeUrl("http://example.com"))
        assertEquals("about:blank", normalizeUrl("about:blank"))
        assertEquals("data:text/html,<h1>hi</h1>", normalizeUrl("data:text/html,<h1>hi</h1>"))
    }

    @Test
    fun coercesBareHostsToHttps() {
        assertEquals("https://example.com", normalizeUrl("example.com"))
        assertEquals("https://a.b.c/path?x=1", normalizeUrl("a.b.c/path?x=1"))
        assertEquals("https://127.0.0.1:8080", normalizeUrl("127.0.0.1:8080"))
        assertEquals("https://localhost:3000", normalizeUrl("localhost:3000"))
        assertEquals("https://localhost", normalizeUrl("localhost"))
    }

    @Test
    fun treatsNonUrlInputAsSearch() {
        assertEquals("https://duckduckgo.com/?q=best+pizza+recipes", normalizeUrl("best pizza recipes"))
        // A single bare word with no dot is a search, not a host.
        assertEquals("https://duckduckgo.com/?q=kotlin", normalizeUrl("kotlin"))
        // Query-ish text with special chars is URL-encoded.
        assertEquals("https://duckduckgo.com/?q=c%2B%2B+vs+rust", normalizeUrl("c++ vs rust"))
    }

    @Test
    fun blankReturnsHome() {
        assertEquals("https://example.com", normalizeUrl("   "))
    }

    @Test
    fun looksLikeUrlHeuristic() {
        assertTrue(looksLikeUrl("example.com"))
        assertTrue(looksLikeUrl("localhost"))
        assertTrue(looksLikeUrl("sub.domain.co.uk/path"))
        assertFalse(looksLikeUrl("hello world"))
        assertFalse(looksLikeUrl("kotlin"))
        // Leading/trailing dots don't make it a host.
        assertFalse(looksLikeUrl(".com"))
        assertFalse(looksLikeUrl("word."))
    }
}
