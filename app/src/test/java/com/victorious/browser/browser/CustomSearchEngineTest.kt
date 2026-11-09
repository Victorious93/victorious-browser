package com.victorious.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure custom-search-engine logic. Victorious Framework. */
class CustomSearchEngineTest {

    @Test fun validAbsoluteHttpUrlsAreValid() {
        assertTrue(CustomSearchEngine.isValid("https://searx.be/search?q=%s"))
        assertTrue(CustomSearchEngine.isValid("http://example.com/?q="))
        assertTrue(CustomSearchEngine.isValid("  https://e.com/s?q=%s  ")) // trimmed
        assertTrue(CustomSearchEngine.isValid("HTTPS://E.COM/s?q=")) // scheme case-insensitive
    }

    @Test fun invalidTemplatesRejected() {
        assertFalse(CustomSearchEngine.isValid(""))
        assertFalse(CustomSearchEngine.isValid("   "))
        assertFalse(CustomSearchEngine.isValid("ftp://example.com/?q=%s"))
        assertFalse(CustomSearchEngine.isValid("example.com/?q=%s")) // no scheme
        assertFalse(CustomSearchEngine.isValid("https://")) // no host
        assertFalse(CustomSearchEngine.isValid("https:///path")) // empty host
    }

    @Test fun placeholderIsSubstitutedAndEncoded() {
        assertEquals(
            "https://searx.be/search?q=best+pizza&safe=1",
            CustomSearchEngine.buildUrl("https://searx.be/search?q=%s&safe=1", "best pizza"),
        )
    }

    @Test fun everyPlaceholderOccurrenceIsReplaced() {
        assertEquals(
            "https://e.com/%s/x?q=foo".replace("%s", "foo"),
            CustomSearchEngine.buildUrl("https://e.com/%s/x?q=%s", "foo"),
        )
    }

    @Test fun prefixTemplateAppendsEncodedQuery() {
        assertEquals(
            "https://duckduckgo.com/?q=hello+world",
            CustomSearchEngine.buildUrl("https://duckduckgo.com/?q=", "hello world"),
        )
    }

    @Test fun specialCharactersAreEncoded() {
        assertEquals(
            "https://e.com/?q=a%26b%3Dc",
            CustomSearchEngine.buildUrl("https://e.com/?q=%s", "a&b=c"),
        )
    }

    @Test fun invalidTemplateReturnsNullFromBuild() {
        assertNull(CustomSearchEngine.buildUrl("not a url", "x"))
        assertNull(CustomSearchEngine.buildUrl("", "x"))
    }
}
