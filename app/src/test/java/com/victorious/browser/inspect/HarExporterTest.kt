package com.victorious.browser.inspect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarExporterTest {

    private fun exchange(): CapturedExchange = CapturedExchange(
        id = 7,
        method = "POST",
        url = "https://app.example/login?token=secret&q=ok",
        host = "app.example",
        scheme = "https",
        requestHeaders = listOf(
            CapturedHeader("Cookie", "sid=abc"),
            CapturedHeader("Authorization", "Bearer zzz"),
            CapturedHeader("Content-Type", "application/json"),
            CapturedHeader("Accept", "application/json"),
        ),
        requestStartedAt = 1_700_000_000_000L,
        statusCode = 200,
        responseHeaders = listOf(
            CapturedHeader("Set-Cookie", "sid=def"),
            CapturedHeader("Content-Type", "application/json"),
        ),
        requestBytes = 12,
        responseBytes = 34,
        requestBody = """{"password":"pw"}""".toByteArray(),
        responseBody = """{"token":"nope"}""".toByteArray(),
    )

    @Test
    fun stripsSecretsAndOmitsBodies() {
        val har = HarExporter.toHar(listOf(exchange()), exportedAt = 99L)
        assertTrue(har.contains("\"version\": \"1.2\""))
        assertTrue(har.contains("Victorious Browser"))
        assertTrue(har.contains("secret-stripped"))
        assertTrue(har.contains("https://app.example/login?q=ok"))
        assertTrue(har.contains("\"name\": \"Accept\""))
        assertTrue(har.contains("\"name\": \"Content-Type\""))
        // secrets gone
        assertFalse(har.contains("sid=abc"))
        assertFalse(har.contains("sid=def"))
        assertFalse(har.contains("Bearer zzz"))
        assertFalse(har.contains("token=secret"))
        assertFalse(har.contains("\"password\":\"pw\""))
        assertFalse(har.contains("nope"))
        assertTrue(har.contains("body omitted"))
        assertTrue(har.contains("\"exportedAt\": 99"))
    }

    @Test
    fun emptyListStillValidHar() {
        val har = HarExporter.toHar(emptyList())
        assertTrue(har.contains("\"entries\": []"))
    }
}
