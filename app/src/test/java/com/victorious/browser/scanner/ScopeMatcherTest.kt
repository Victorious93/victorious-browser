package com.victorious.browser.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopeMatcherTest {
    @Test fun hostOf_parsesHost() {
        assertEquals("example.com", ScopeMatcher.hostOf("https://example.com:8443/a?b=1"))
    }
    @Test fun hostOf_invalid_null() {
        assertEquals(null, ScopeMatcher.hostOf("not a url"))
    }
    @Test fun normalizeEntry_stripsSchemePortPath() {
        assertEquals("example.com", ScopeMatcher.normalizeEntry("HTTPS://Example.com:443/foo"))
        assertEquals("*.example.com", ScopeMatcher.normalizeEntry("*.Example.com"))
    }
    @Test fun exactMatch() {
        assertTrue(ScopeMatcher.hostMatchesEntry("example.com", "example.com"))
        assertFalse(ScopeMatcher.hostMatchesEntry("evil.com", "example.com"))
        assertFalse(ScopeMatcher.hostMatchesEntry("sub.example.com", "example.com"))
    }
    @Test fun wildcardMatchesSubAndApex() {
        assertTrue(ScopeMatcher.hostMatchesEntry("a.example.com", "*.example.com"))
        assertTrue(ScopeMatcher.hostMatchesEntry("example.com", "*.example.com"))
        assertFalse(ScopeMatcher.hostMatchesEntry("notexample.com", "*.example.com"))
        assertFalse(ScopeMatcher.hostMatchesEntry("example.com.evil.com", "*.example.com"))
    }
    @Test fun inScope_emptyAllow_false() {
        assertFalse(ScopeMatcher.inScope("https://example.com/", emptyList()))
    }
    @Test fun inScope_matches() {
        assertTrue(ScopeMatcher.inScope("https://api.example.com/x", listOf("*.example.com")))
        assertFalse(ScopeMatcher.inScope("https://other.com/x", listOf("example.com")))
    }
}
