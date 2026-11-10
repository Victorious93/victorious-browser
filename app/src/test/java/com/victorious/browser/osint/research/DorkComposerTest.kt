package com.victorious.browser.osint.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DorkComposerTest {
    @Test fun tokensFormat() {
        assertEquals("site:example.com", DorkComposer.token(DorkComposer.Op.SITE, " example.com "))
        assertEquals("intitle:\"index of\"", DorkComposer.token(DorkComposer.Op.INTITLE, "index of"))
        assertEquals("\"exact phrase\"", DorkComposer.token(DorkComposer.Op.EXACT, "exact \"phrase\""))
        assertEquals("-login", DorkComposer.token(DorkComposer.Op.EXCLUDE, "-login"))
        assertEquals("filetype:pdf", DorkComposer.token(DorkComposer.Op.FILETYPE, "pdf"))
        assertEquals("", DorkComposer.token(DorkComposer.Op.SITE, "  "))
        assertEquals("OR", DorkComposer.token(DorkComposer.Op.OR))
    }

    @Test fun dateOperatorsValidate() {
        assertEquals("before:2024-01-01", DorkComposer.token(DorkComposer.Op.BEFORE, "2024-01-01"))
        assertEquals("after:2023", DorkComposer.token(DorkComposer.Op.AFTER, "2023"))
        assertEquals("", DorkComposer.token(DorkComposer.Op.AFTER, "last year"))
    }

    @Test fun buildDropsDanglingOr() {
        assertEquals("site:a.com ext:sql OR ext:bak", DorkComposer.build(listOf("OR", "site:a.com", " ", "ext:sql", "OR", "OR", "ext:bak", "OR")))
    }

    @Test fun searchUrls() {
        assertEquals("https://www.google.com/search?q=site%3Aa.com%20%22x%20y%22",
            DorkComposer.searchUrl(DorkComposer.Engine.GOOGLE, "site:a.com \"x y\""))
        assertEquals("https://www.bing.com/search?q=a", DorkComposer.searchUrl(DorkComposer.Engine.BING, "a"))
        assertEquals("https://duckduckgo.com/?q=a", DorkComposer.searchUrl(DorkComposer.Engine.DUCKDUCKGO, "a"))
        assertNull(DorkComposer.searchUrl(DorkComposer.Engine.GOOGLE, "  "))
    }

    @Test fun customEngine() {
        assertTrue(DorkComposer.isValidCustomTemplate("https://s.example/?q=%s"))
        assertTrue(DorkComposer.isValidCustomTemplate("https://s.example/search/{q}"))
        assertFalse(DorkComposer.isValidCustomTemplate("javascript:alert(%s)"))
        assertFalse(DorkComposer.isValidCustomTemplate("https://s.example/"))
        assertEquals("https://s.example/?q=a%20b", DorkComposer.searchUrl(DorkComposer.Engine.CUSTOM, "a b", "https://s.example/?q=%s"))
        assertNull(DorkComposer.searchUrl(DorkComposer.Engine.CUSTOM, "a", "ftp://x/%s"))
    }

    @Test fun libraryExpands() {
        val d = DorkLibrary.all.first { it.name == "Open index pages" }
        assertEquals("site:example.com intitle:\"index of\"", DorkLibrary.expand(d, "https://example.com/", ""))
        assertNull(DorkLibrary.expand(d, "", "kw"))
        val k = DorkLibrary.all.first { it.needsKeyword && !it.needsDomain }
        assertNull(DorkLibrary.expand(k, "example.com", ""))
        assertTrue(DorkLibrary.categories.size >= 8)
        DorkLibrary.all.forEach { assertTrue(it.name, it.needsDomain || it.needsKeyword) }
    }
}
