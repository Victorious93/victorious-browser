package com.victorious.browser.urltools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlToolsTest {
    @Test
    fun encodesAndDecodesRoundTrip() {
        val raw = "a b&c=d/e?f"
        val encoded = UrlTools.encode(raw)
        assertEquals(raw, UrlTools.decode(encoded))
    }

    @Test
    fun decodeReturnsInputUnchangedOnMalformedPercentEncoding() {
        assertEquals("100% done", UrlTools.decode("100% done"))
    }

    @Test
    fun parsesQueryParamsInOrder() {
        val params = UrlTools.parseQueryParams("https://example.com/path?a=1&b=two%20words&c")
        assertEquals(3, params.size)
        assertEquals(UrlTools.QueryParam("a", "1"), params[0])
        assertEquals(UrlTools.QueryParam("b", "two words"), params[1])
        assertEquals(UrlTools.QueryParam("c", ""), params[2])
    }

    @Test
    fun parsesBareQueryStringWithoutUrl() {
        assertEquals(listOf(UrlTools.QueryParam("x", "y")), UrlTools.parseQueryParams("x=y"))
    }

    @Test
    fun returnsEmptyForUrlWithNoQuery() {
        assertTrue(UrlTools.parseQueryParams("https://example.com/path").isEmpty())
    }

    @Test
    fun buildUrlStripsExistingQueryAndFragmentThenAppendsParams() {
        val built = UrlTools.buildUrl(
            "https://example.com/path?old=1#frag",
            listOf(UrlTools.QueryParam("a", "1"), UrlTools.QueryParam("b", "two words")),
        )
        assertEquals("https://example.com/path?a=1&b=two+words", built)
    }

    @Test
    fun buildUrlWithNoParamsReturnsBareBase() {
        assertEquals("https://example.com/path", UrlTools.buildUrl("https://example.com/path?a=1", emptyList()))
    }

    @Test
    fun partsRoundTripThroughParseAndBuild() {
        val url = "https://example.com:8443/a/b?x=1&y=2#frag"
        val parts = UrlTools.parseParts(url)
        assertEquals(
            UrlTools.UrlParts("https", "example.com", 8443, "/a/b", "x=1&y=2", "frag"),
            parts,
        )
        assertEquals(url, UrlTools.buildFromParts(parts!!))
    }

    @Test
    fun parsePartsOmitsPortWhenAbsentAndRoundTripDoesNotInventOne() {
        val parts = UrlTools.parseParts("https://example.com/path")
        assertEquals(null, parts?.port)
        assertEquals("https://example.com/path", UrlTools.buildFromParts(parts!!))
    }

    @Test
    fun parsePartsReturnsNullForNonAbsoluteUrls() {
        assertEquals(null, UrlTools.parseParts("not a url"))
        assertEquals(null, UrlTools.parseParts("/just/a/path"))
    }

    @Test
    fun buildFromPartsOmitsQueryAndFragmentWhenBlank() {
        val parts = UrlTools.UrlParts("https", "example.com", null, "/p", "", "")
        assertEquals("https://example.com/p", UrlTools.buildFromParts(parts))
    }
}
