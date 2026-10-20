package com.victorious.browser.scanner

import com.victorious.browser.inspect.CapturedHeader
import com.victorious.browser.inspect.EditableRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsertionPointsTest {
    @Test fun enumerate_queryBodyHeaders() {
        val req = EditableRequest(
            method = "POST",
            url = "https://example.com/s?q=hello&page=1",
            headers = listOf(
                CapturedHeader("Content-Type", "application/x-www-form-urlencoded"),
                CapturedHeader("User-Agent", "ua"),
                CapturedHeader("Accept", "*/*"),
            ),
            body = "name=bob&age=3".toByteArray(),
        )
        val pts = InsertionPoints.enumerate(req)
        assertTrue(pts.any { it.kind == InsertionPoints.Kind.QUERY && it.name == "q" })
        assertTrue(pts.any { it.kind == InsertionPoints.Kind.BODY && it.name == "name" })
        assertTrue(pts.any { it.kind == InsertionPoints.Kind.HEADER && it.name == "User-Agent" })
        // Accept is not injectable
        assertTrue(pts.none { it.name == "Accept" })
    }

    @Test fun apply_queryReplacesValueEncoded() {
        val req = EditableRequest("GET", "https://example.com/s?q=hello&p=1", emptyList())
        val pt = InsertionPoints.Point(InsertionPoints.Kind.QUERY, "q", "hello")
        val out = InsertionPoints.apply(req, pt, "a b<")
        assertTrue(out.url.contains("q=a+b%3C"))
        assertTrue(out.url.contains("p=1"))
    }

    @Test fun apply_bodyReplacesValue() {
        val req = EditableRequest(
            "POST", "https://example.com/s",
            listOf(CapturedHeader("Content-Type", "application/x-www-form-urlencoded")),
            "name=bob&age=3".toByteArray(),
        )
        val pt = InsertionPoints.Point(InsertionPoints.Kind.BODY, "name", "bob")
        val out = InsertionPoints.apply(req, pt, "x'")
        assertEquals("name=x%27&age=3", out.bodyAsUtf8())
    }

    @Test fun apply_headerReplacesValue() {
        val req = EditableRequest(
            "GET", "https://example.com/",
            listOf(CapturedHeader("User-Agent", "ua")),
        )
        val pt = InsertionPoints.Point(InsertionPoints.Kind.HEADER, "User-Agent", "ua")
        val out = InsertionPoints.apply(req, pt, "evil")
        assertEquals("evil", out.headers.first { it.name == "User-Agent" }.value)
    }

    @Test fun parseQueryString_handlesNoValue() {
        val ps = InsertionPoints.parseQueryString("a=1&flag&b=2")
        assertEquals(3, ps.size)
        assertEquals("flag" to "", ps[1])
    }
}
