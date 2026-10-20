package com.victorious.browser.osint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DorkBuilderTest {

    @Test
    fun operator_formatsSimpleValue() {
        assertEquals("site:example.com", DorkBuilder.Operator.SITE.format("example.com"))
        assertEquals("filetype:pdf", DorkBuilder.Operator.FILETYPE.format("pdf"))
        assertEquals("inurl:admin", DorkBuilder.Operator.INURL.format("admin"))
    }

    @Test
    fun operator_quotesValuesWithSpaces() {
        assertEquals("intitle:\"index of\"", DorkBuilder.Operator.INTITLE.format("index of"))
    }

    @Test
    fun operator_blankYieldsEmpty() {
        assertEquals("", DorkBuilder.Operator.SITE.format("  "))
    }

    @Test
    fun exact_and_exclude() {
        assertEquals("\"api key\"", DorkBuilder.exact("api key"))
        assertEquals("-login", DorkBuilder.exclude("login"))
        assertEquals("", DorkBuilder.exact(" "))
    }

    @Test
    fun boolean_helpers() {
        assertEquals("a OR b OR c", DorkBuilder.or("a", "b", "c"))
        assertEquals("a AND b", DorkBuilder.and("a", "b"))
        assertEquals("a", DorkBuilder.or("a", "", "  "))
    }

    @Test
    fun wildcard_and_range() {
        assertEquals("password * file", DorkBuilder.wildcard("password", "file"))
        assertEquals("*", DorkBuilder.wildcard("", ""))
        assertEquals("2020..2024", DorkBuilder.numericRange("2020", "2024"))
        assertEquals("..10", DorkBuilder.numericRange("", "10"))
        assertEquals("", DorkBuilder.numericRange("", ""))
    }

    @Test
    fun buildQuery_joinsAndCollapses() {
        val q = DorkBuilder.buildQuery(
            listOf("site:example.com", "  ", "filetype:pdf", "inurl:admin"),
        )
        assertEquals("site:example.com filetype:pdf inurl:admin", q)
    }

    @Test
    fun searchUrl_google_encodesSpaces() {
        val url = DorkBuilder.searchUrl(DorkBuilder.Engine.GOOGLE, "site:example.com filetype:pdf")
        assertTrue(url.startsWith("https://www.google.com/search?q="))
        assertTrue(url.contains("site%3Aexample.com"))
        assertTrue(url.contains("%20") || url.contains("+")) // space encoded
        assertFalse(url.contains(" "))
    }

    @Test
    fun searchUrl_duckduckgo_and_bing() {
        assertTrue(
            DorkBuilder.searchUrl(DorkBuilder.Engine.DUCKDUCKGO, "x")
                .startsWith("https://duckduckgo.com/?q="),
        )
        assertTrue(
            DorkBuilder.searchUrl(DorkBuilder.Engine.BING, "x")
                .startsWith("https://www.bing.com/search?q="),
        )
    }

    @Test
    fun templates_includeSiteAndTokens() {
        assertTrue(DorkBuilder.templates.size >= 5)
        val login = DorkBuilder.templates.first { it.name == "Login pages" }
        val tokens = login.build("target.example")
        val q = DorkBuilder.buildQuery(tokens)
        assertTrue(q.contains("site:target.example"))
        assertTrue(q.contains("login") || q.contains("admin"))
    }

    @Test
    fun templates_directoryListings_hasExactPhrase() {
        val dir = DorkBuilder.templates.first { it.name == "Directory listings" }
        val q = DorkBuilder.buildQuery(dir.build("corp.example"))
        assertTrue(q.contains("\"parent directory\""))
        assertTrue(q.contains("intitle:\"index of\"") || q.contains("intitle:index"))
    }
}
