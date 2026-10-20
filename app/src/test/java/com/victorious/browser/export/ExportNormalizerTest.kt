package com.victorious.browser.export

import com.victorious.browser.inspect.CapturedExchange
import com.victorious.browser.inspect.CapturedHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportNormalizerTest {
    private fun ex(url: String, headers: List<CapturedHeader>) = CapturedExchange(
        id = 1,
        method = "GET",
        url = url,
        host = "h",
        scheme = "https",
        requestHeaders = headers,
        requestStartedAt = 0L,
        statusCode = 200,
    )

    @Test fun hostOfParses() {
        assertEquals("example.com", ExportNormalizer.hostOf("https://example.com/a?b=1"))
        assertEquals(null, ExportNormalizer.hostOf("not a url"))
    }

    @Test fun endpointStripsSecretsFromHeadersAndQuery() {
        val e = ex(
            "https://example.com/api?q=1&token=abc",
            listOf(CapturedHeader("Accept", "*/*"), CapturedHeader("Cookie", "sid=1")),
        )
        val ep = ExportNormalizer.endpointOf(e)
        assertEquals("https://example.com/api?q=1", ep.url)
        assertTrue(ep.params.containsKey("q"))
        assertFalse(ep.params.containsKey("token"))
        assertTrue(ep.headers!!.containsKey("Accept"))
        assertFalse(ep.headers!!.containsKey("Cookie"))
        assertEquals(200, ep.statusCode)
    }

    @Test fun buildPayloadMergesTargetsAndStripsSecrets() {
        val exchanges = listOf(
            ex("https://a.com/x?password=p", listOf(CapturedHeader("Authorization", "Bearer z"))),
            ex("https://b.com/y", emptyList()),
        )
        val payload = ExportNormalizer.buildPayload(
            exchanges = exchanges,
            extraTargets = listOf("seed.com"),
            now = 42L,
        )
        assertEquals(EXPORT_SOURCE, payload.source)
        assertEquals(42L, payload.exportedAt)
        assertEquals(2, payload.endpoints.size)
        assertTrue(payload.targets.containsAll(listOf("seed.com", "a.com", "b.com")))
        val json = payload.toJsonString()
        assertFalse(json.contains("password"))
        assertFalse(json.contains("Authorization"))
        assertFalse(json.contains("Bearer"))
    }

    @Test fun jsonHasExpectedShape() {
        val payload = ExportNormalizer.buildPayload(now = 100L)
        val o = payload.toJson()
        assertEquals("com.victorious.browser", o.getString("source"))
        assertTrue(o.has("targets"))
        assertTrue(o.has("endpoints"))
        assertTrue(o.has("findings"))
        assertTrue(o.has("counts"))
    }
}
