package com.victorious.browser.inspect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure "Copy as cURL" formatter. Victorious Framework. */
class CurlFormatterTest {

    private fun h(vararg pairs: Pair<String, String>) = pairs.map { CapturedHeader(it.first, it.second) }

    @Test fun simpleGetOmitsMethodFlag() {
        val c = CurlFormatter.toCurl("GET", "https://example.com/", emptyList())
        assertEquals("curl 'https://example.com/'", c)
    }

    @Test fun nonGetMethodIsExplicit() {
        val c = CurlFormatter.toCurl("DELETE", "https://example.com/x", emptyList())
        assertEquals("curl -X 'DELETE' 'https://example.com/x'", c)
    }

    @Test fun methodIsUppercasedAndBlankDefaultsToGet() {
        assertEquals("curl -X 'POST' 'https://e/' --data-raw 'x'",
            CurlFormatter.toCurl("post", "https://e/", emptyList(), "x".toByteArray()))
        assertEquals("curl 'https://e/'", CurlFormatter.toCurl("   ", "https://e/", emptyList()))
    }

    @Test fun headersAreEmittedInOrder() {
        val c = CurlFormatter.toCurl(
            "GET", "https://example.com/",
            h("Accept" to "application/json", "X-Trace" to "abc"),
        )
        assertEquals("curl 'https://example.com/' -H 'Accept: application/json' -H 'X-Trace: abc'", c)
    }

    @Test fun http2PseudoHeadersAndContentLengthAreDropped() {
        val c = CurlFormatter.toCurl(
            "POST", "https://example.com/api",
            h(":method" to "POST", ":authority" to "example.com", "Content-Length" to "4", "Accept" to "*/*"),
            "data".toByteArray(),
        )
        assertFalse(c.contains(":method"))
        assertFalse(c.contains(":authority"))
        assertFalse(c.contains("Content-Length"))
        assertTrue(c.contains("-H 'Accept: */*'"))
        assertTrue(c.endsWith("--data-raw 'data'"))
    }

    @Test fun bodyAttachedWithDataRawAndForcesMethod() {
        val c = CurlFormatter.toCurl("POST", "https://example.com/", emptyList(), """{"a":1}""".toByteArray())
        assertEquals("""curl -X 'POST' 'https://example.com/' --data-raw '{"a":1}'""", c)
    }

    @Test fun emptyBodyIsNotAttached() {
        val c = CurlFormatter.toCurl("POST", "https://example.com/", emptyList(), ByteArray(0))
        assertEquals("curl -X 'POST' 'https://example.com/'", c)
        assertFalse(c.contains("--data-raw"))
    }

    @Test fun singleQuotesInValuesAreEscaped() {
        // Header value containing a single quote must be shell-safe.
        val c = CurlFormatter.toCurl(
            "GET", "https://example.com/search?q=o'brien",
            h("X-Note" to "it's fine"),
        )
        // ' -> '\'' ; URL and header both quoted.
        assertEquals(
            "curl 'https://example.com/search?q=o'\\''brien' -H 'X-Note: it'\\''s fine'",
            c,
        )
    }

    @Test fun exchangeOverloadUsesRequestFields() {
        val ex = CapturedExchange(
            id = 1L, method = "POST", url = "https://api.example.com/v1", host = "api.example.com",
            scheme = "https", requestHeaders = h("Authorization" to "Bearer t0ken"),
            requestStartedAt = 0L, requestBody = "payload".toByteArray(),
        )
        assertEquals(
            "curl -X 'POST' 'https://api.example.com/v1' -H 'Authorization: Bearer t0ken' --data-raw 'payload'",
            CurlFormatter.toCurl(ex),
        )
    }

    @Test fun editableRequestOverload() {
        val req = EditableRequest("PUT", "https://e/x", h("X-A" to "1"), "b".toByteArray())
        assertEquals("curl -X 'PUT' 'https://e/x' -H 'X-A: 1' --data-raw 'b'", CurlFormatter.toCurl(req))
    }
}
