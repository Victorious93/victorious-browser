package com.victorious.browser.webext

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlMatcherTest {
    @Test fun matchPatternAllUrls() {
        assertTrue(MatchPattern.matches("<all_urls>", "https://a.com/x"))
        assertFalse(MatchPattern.matches("<all_urls>", "chrome://settings"))
    }

    @Test fun matchPatternSchemeHostPath() {
        assertTrue(MatchPattern.matches("*://*.example.com/*", "https://www.example.com/a"))
        assertTrue(MatchPattern.matches("*://*.example.com/*", "http://example.com/"))
        assertFalse(MatchPattern.matches("*://*.example.com/*", "https://evil.com/"))
        assertTrue(MatchPattern.matches("https://example.com/foo*", "https://example.com/foobar"))
        assertFalse(MatchPattern.matches("https://example.com/foo*", "http://example.com/foobar"))
    }

    @Test fun matchPatternStarSchemeHttpHttpsOnly() {
        assertFalse(MatchPattern.matches("*://example.com/*", "ftp://example.com/x"))
    }

    @Test fun globBasics() {
        assertTrue(Glob.matches("a*c", "abc"))
        assertTrue(Glob.matches("*", "anything"))
        assertFalse(Glob.matches("a*c", "ab"))
    }

    @Test fun includeGlobAndRegex() {
        assertTrue(UrlMatcher.matchesRule("*://*.test.com/*", "https://a.test.com/x"))
        assertTrue(UrlMatcher.matchesRule("*example*", "https://example.com/"))
        assertTrue(UrlMatcher.matchesRule("/^https:\\/\\/foo\\.bar\\/.*/", "https://foo.bar/z"))
        assertTrue(UrlMatcher.matchesRule("/FOO/i", "https://x/foo"))
        assertFalse(UrlMatcher.matchesRule("/FOO/", "https://x/foo"))
    }

    @Test fun excludesWin() {
        val meta = UserscriptMeta(
            name = "t",
            matches = listOf("*://*/*"),
            excludes = listOf("*://admin.example.com/*"),
        )
        assertTrue(UrlMatcher.shouldRun(meta, "https://example.com/"))
        assertFalse(UrlMatcher.shouldRun(meta, "https://admin.example.com/x"))
    }

    @Test fun includeOnlyWithoutMatch() {
        val meta = UserscriptMeta(name = "t", includes = listOf("*://ok.test/*"))
        assertTrue(UrlMatcher.shouldRun(meta, "https://ok.test/a"))
        assertFalse(UrlMatcher.shouldRun(meta, "https://no.test/a"))
    }

    @Test fun urlPartsParse() {
        val u = UrlParts.parse("https://user:pass@Host.Example:8443/a/b?q=1#frag")!!
        assertTrue(u.scheme == "https")
        assertTrue(u.host == "host.example")
        assertTrue(u.pathAndQuery.startsWith("/a/b"))
        assertTrue(u.pathAndQuery.contains("q=1"))
        assertFalse(u.pathAndQuery.contains("frag"))
    }
}
