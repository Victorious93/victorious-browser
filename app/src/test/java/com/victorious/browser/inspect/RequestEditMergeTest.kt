package com.victorious.browser.inspect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestEditMergeTest {

    @Test
    fun fromExchange_copiesFieldsAndBody() {
        val body = """{"a":1}""".toByteArray()
        val ex = CapturedExchange(
            id = 7,
            method = "POST",
            url = "https://127.0.0.1:8443/api",
            host = "127.0.0.1",
            scheme = "https",
            requestHeaders = listOf(
                CapturedHeader("Host", "127.0.0.1:8443"),
                CapturedHeader("Content-Type", "application/json"),
                CapturedHeader("Connection", "keep-alive"),
            ),
            requestStartedAt = 1L,
            requestBody = body,
        )
        val editable = RequestEditMerge.fromExchange(ex)
        assertEquals("POST", editable.method)
        assertEquals("https://127.0.0.1:8443/api", editable.url)
        assertEquals("""{"a":1}""", editable.bodyAsUtf8())
        assertFalse(editable.headers.any { it.name.equals("Connection", true) })
        assertTrue(editable.headers.any { it.name.equals("Content-Type", true) })
    }

    @Test
    fun merge_overridesMethodUrlHeadersBody_andSyncsContentLength() {
        val base = EditableRequest(
            method = "GET",
            url = "http://example.local/",
            headers = listOf(CapturedHeader("Accept", "*/*")),
            body = null,
        )
        val merged = RequestEditMerge.merge(
            base = base,
            method = "post",
            url = "http://127.0.0.1:8080/v1/echo",
            headersText = "Accept: application/json\nX-Trace: 1\n",
            bodyText = "hello",
        )
        assertEquals("POST", merged.method)
        assertEquals("http://127.0.0.1:8080/v1/echo", merged.url)
        assertEquals("hello", merged.bodyAsUtf8())
        assertEquals("127.0.0.1:8080", merged.headers.first { it.name.equals("Host", true) }.value)
        assertEquals("5", merged.headers.first { it.name.equals("Content-Length", true) }.value)
        assertTrue(merged.headers.any { it.name == "X-Trace" && it.value == "1" })
    }

    @Test
    fun merge_emptyBodyText_clearsBodyAndDropsContentLength() {
        val base = EditableRequest(
            method = "POST",
            url = "http://127.0.0.1/x",
            headers = listOf(CapturedHeader("Content-Length", "4")),
            body = "abcd".toByteArray(),
        )
        val merged = RequestEditMerge.merge(base, bodyText = "")
        assertNull(merged.body)
        assertFalse(merged.headers.any { it.name.equals("Content-Length", true) })
    }

    @Test
    fun parseHeadersText_skipsMalformedAndHopByHop() {
        val parsed = RequestEditMerge.parseHeadersText(
            """
            Host: a.example
            Connection: close
            X-Ok: yes
            not-a-header
            Proxy-Connection: keep-alive
            """.trimIndent(),
        )
        assertEquals(2, parsed.size)
        assertEquals("Host", parsed[0].name)
        assertEquals("X-Ok", parsed[1].name)
    }

    @Test
    fun requestUriForProxy_originVsAbsolute() {
        val url = "https://fleet.local:9443/path?q=1"
        assertEquals("/path?q=1", RequestEditMerge.requestUriForProxy(url, preferAbsolute = false))
        assertEquals(url, RequestEditMerge.requestUriForProxy(url, preferAbsolute = true))
    }

    @Test
    fun ensureHostHeader_replacesExisting() {
        val headers = listOf(
            CapturedHeader("Host", "old"),
            CapturedHeader("Accept", "text/plain"),
        )
        val out = RequestEditMerge.ensureHostHeader(headers, "http://new.host:9/x")
        assertEquals(1, out.count { it.name.equals("Host", true) })
        assertEquals("new.host:9", out.first { it.name.equals("Host", true) }.value)
        assertTrue(out.any { it.name == "Accept" })
    }

    @Test
    fun isConnect() {
        assertTrue(RequestEditMerge.isConnect("CONNECT"))
        assertTrue(RequestEditMerge.isConnect("connect"))
        assertFalse(RequestEditMerge.isConnect("GET"))
    }
}
